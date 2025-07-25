// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.impl.ProjectUtil.openOrImportFilesAsync
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.wm.WelcomeScreenCustomization
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectCollectors
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectPanelComponentFactory.createComponent
import com.intellij.openapi.wm.impl.welcomeScreen.statistics.WelcomeScreenCounterUsageCollector
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants


@Suppress("OVERRIDE_DEPRECATION")
internal class ProjectsTabFactory : WelcomeTabFactory {
  override fun createWelcomeTab(parentDisposable: Disposable): WelcomeScreenTab = ProjectsTab(parentDisposable)
}

internal class ProjectsTab(private val parentDisposable: Disposable) : DefaultWelcomeScreenTab(
  IdeBundle.message("welcome.screen.projects.title"),
  WelcomeScreenEventCollector.TabType.TabNavProject
) {
  private val projectsPanelWrapper: Wrapper = Wrapper()
  private val recentProjectsPanel: JComponent = createRecentProjectsPanel()
  private val emptyStatePanel: JComponent = createEmptyStatePanel()
  private val notificationPanel: JComponent = WelcomeScreenComponentFactory.createNotificationToolbar(parentDisposable)
  private var panelState: PanelState

  init {
    panelState = getCurrentState()
    updateState(panelState)
    val connect = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    connect.subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
      override fun onCloneCanceled() {}
      override fun onCloneFailed() {}
      override fun onCloneSuccess() {}
      override fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
        checkState()
      }

      override fun onCloneRemoved() {
        checkState()
      }
    })
    connect.subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
      override fun change() {
        checkState()
      }
    })
  }

  override fun buildComponent(): JComponent {
    val recentProjectsCount = RecentProjectListActionProvider.getInstance().countLocalProjects()
    val providerRecentProjectsCount = RecentProjectListActionProvider.getInstance().countProjectsFromProviders()
    WelcomeScreenCounterUsageCollector.reportWelcomeScreenShowed(recentProjectsCount, providerRecentProjectsCount)

    val promo = WelcomeScreenComponentFactory.getSinglePromotion(recentProjectsCount == 0)

    return panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          cell(projectsPanelWrapper)
            .align(Align.FILL)
        }.resizableRow()
        row {
          cell(notificationPanel)
            .align(AlignX.RIGHT)
            .applyToComponent {
              putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
            }
        }
        if (promo != null) {
          row {
            cell(promo)
              .customize(UnscaledGaps(0, PROMO_BORDER_OFFSET, PROMO_BORDER_OFFSET, PROMO_BORDER_OFFSET))
              .align(AlignX.FILL)
              .applyToComponent {
                putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
              }
          }
        }
      }
    }.apply {
      background = WelcomeScreenUIManager.getProjectsBackground()
    }
  }

  private fun checkState() {
    val currentState = getCurrentState()
    if (currentState == panelState) {
      return
    }
    updateState(currentState)
  }

  private fun updateState(currentPanelState: PanelState) {
    if (currentPanelState == PanelState.EMPTY) {
      projectsPanelWrapper.setContent(emptyStatePanel)
    }
    else {
      projectsPanelWrapper.setContent(recentProjectsPanel)
    }
    panelState = currentPanelState
    projectsPanelWrapper.repaint()
  }

  override fun updateComponent() {
    // balloonLayout is not initialized at this point
    invokeLater {
      val balloonLayout = WelcomeFrame.getInstance()?.balloonLayout
      if (balloonLayout is WelcomeBalloonLayoutImpl) {
        balloonLayout.locationComponent = notificationPanel
      }
    }
  }

  private fun createRecentProjectsPanel(): JComponent {
    val recentProjectsPanel: JPanel = JBUI.Panels.simplePanel()
      .withBorder(JBUI.Borders.empty(13, 12))
      .withBackground(WelcomeScreenUIManager.getProjectsBackground())
    val recentProjectTree = createComponent(
      parentDisposable, ProjectCollectors.all
    )
    recentProjectTree.selectLastOpenedProject()

    val treeComponent = recentProjectTree.component
    val scrollPane = ScrollPaneFactory.createScrollPane(treeComponent, true)
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.isOpaque = false
    val projectsPanel = JBUI.Panels.simplePanel(scrollPane)
      .andTransparent()
      .withBorder(JBUI.Borders.emptyTop(10))

    val projectSearch = recentProjectTree.installSearchField()
    if (ExperimentalUI.isNewUI()) {
      projectSearch.textEditor.putClientProperty("JTextField.Search.Icon", AllIcons.Actions.Search)
    }
    val northPanel: JPanel = JBUI.Panels.simplePanel()
      .andTransparent()
      .withBorder(object : CustomLineBorder(WelcomeScreenUIManager.getSeparatorColor(), JBUI.insetsBottom(1)) {
        override fun getBorderInsets(c: Component): Insets {
          return JBUI.insetsBottom(12)
        }
      })
    val actionsToolbar = createActionsToolbar()
    actionsToolbar.targetComponent = scrollPane
    val projectActionsPanel = actionsToolbar.component
    northPanel.add(projectSearch, BorderLayout.CENTER)
    northPanel.add(projectActionsPanel, BorderLayout.EAST)
    recentProjectsPanel.add(northPanel, BorderLayout.NORTH)
    recentProjectsPanel.add(projectsPanel, BorderLayout.CENTER)

    initDnD(treeComponent)

    return recentProjectsPanel
  }

  private fun createEmptyStatePanel(): JComponent {
    val emptyStateProjectsPanel = WelcomeScreenCustomization.WELCOME_SCREEN_CUSTOMIZATION.extensionList.firstNotNullOfOrNull { it.createMainEmptyState(parentDisposable) }
                                  ?: emptyStateProjectPanel(parentDisposable)
    initDnD(emptyStateProjectsPanel)
    return emptyStateProjectsPanel
  }

  private fun initDnD(component: JComponent) {
    val target = createDropFileTarget()
    DnDSupport.createBuilder(component)
      .enableAsNativeTarget()
      .setTargetChecker(target)
      .setDropHandler(target)
      .setDisposableParent(parentDisposable)
      .install()
  }

  private fun createActionsToolbar(): ActionToolbar {
    val actionManager = ActionManager.getInstance()
    val baseGroup = actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART_PROJECTS_STATE) as ActionGroup
    val toolbarGroup = object : ActionGroupWrapper(baseGroup) {
      val wrappers = ConcurrentHashMap<AnAction, AnAction>()
      override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
        val mapped = visibleChildren.mapIndexed { index, action ->
          when {
            index >= getWelcomeScreenPrimaryButtonsNum() -> action
            action is ActionGroup && action is ActionsWithPanelProvider -> {
              val wrapper = wrappers.getOrPut(action) {
                ActionGroupPanelWrapper.wrapGroups(action, parentDisposable)
              }
              e.updateSession.presentation(wrapper)
              wrapper
            }
            action is ActionGroup -> {
              val children = e.updateSession.children(action).toList()
              when {
                children.isEmpty() -> action
                else -> {
                  val first = children.first()
                  val wrapper = when {
                    first is ActionGroup && first is ActionsWithPanelProvider -> wrappers.getOrPut(first) {
                      ActionGroupPanelWrapper.wrapGroups(first, parentDisposable)
                    }
                    else -> first
                  }
                  e.updateSession.presentation(wrapper).putClientProperty(
                    ActionUtil.INLINE_ACTIONS, children.subList(1, children.size))
                  wrapper
                }
              }
            }
            else -> action
          }
        }
        mapped.forEach { action ->
          e.updateSession.presentation(action).putClientProperty(
            ActionUtil.COMPONENT_PROVIDER, WelcomeScreenActionsUtil.createToolbarTextButtonAction(action))
        }
        return mapped
      }
    }
    val toolbar: ActionToolbarImpl = object : ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, toolbarGroup, true) {
      override fun isSecondaryAction(action: AnAction, actionIndex: Int): Boolean {
        return actionIndex >= getWelcomeScreenPrimaryButtonsNum()
      }

      override fun createToolbarButton(
        action: AnAction,
        look: ActionButtonLook?,
        place: String,
        presentation: Presentation,
        minimumSize: Supplier<out Dimension>,
      ): ActionButton {
        return super.createToolbarButton(action, look, place, presentation, minimumSize).apply {
          isFocusable = true
        }
      }
    }
    toolbar.isOpaque = false
    toolbar.isReservePlaceAutoPopupIcon = false
    toolbar.setSecondaryActionsIcon(AllIcons.Actions.More, true)
    @Suppress("DialogTitleCapitalization")
    toolbar.setSecondaryActionsTooltip(IdeBundle.message("welcome.screen.more.actions.link.text"))
    return toolbar
  }
}

private const val PROMO_BORDER_OFFSET = 16

private enum class PanelState {
  EMPTY, NOT_EMPTY
}

private fun getCurrentState(): PanelState {
  val recentProjects = RecentProjectListActionProvider.getInstance().collectProjects()
  val collectCloneableProjects = CloneableProjectsService.getInstance().collectCloneableProjects().toList()
  return if (recentProjects.isEmpty() && collectCloneableProjects.isEmpty()) {
    PanelState.EMPTY
  }
  else {
    PanelState.NOT_EMPTY
  }
}

private fun createDropFileTarget(): DnDNativeTarget {
  return object : DnDNativeTarget {
    override fun update(event: DnDEvent): Boolean {
      if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
        return false
      }
      event.isDropPossible = true
      return false
    }

    override fun drop(event: DnDEvent) {
      val files = FileCopyPasteUtil.getFileListFromAttachedObject(event.attachedObject)
      if (!files.isEmpty()) {
        service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
          openOrImportFilesAsync(list = files.map(File::toPath), location = "WelcomeFrame")
        }
      }
    }
  }
}