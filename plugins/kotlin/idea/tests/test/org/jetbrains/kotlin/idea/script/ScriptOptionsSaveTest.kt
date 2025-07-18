// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.testFramework.LightProjectDescriptor
import org.jdom.output.XMLOutputter
import org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class ScriptOptionsSaveTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testSaveAutoReload() {
      val project = myFixture.project
      val settings = KotlinScriptingSettingsImpl.getInstance(project)
      val definition = ScriptDefinitionsManager.getInstance(project).getDefinitions().first()
      val initialAutoReload = settings.autoReloadConfigurations(definition)

      settings.setAutoReloadConfigurations(definition, !initialAutoReload)

      assertEquals(
        "isAutoReloadEnabled should be set to true",
        "true",
        XMLOutputter().outputString(settings.state)
          .substringAfter("<autoReloadConfigurations>")
          .substringBefore("</autoReloadConfigurations>")
      )

      settings.setAutoReloadConfigurations(definition, initialAutoReload)
    }

    fun testSaveScriptDefinitionOff() {
      val project = myFixture.project
      val scriptDefinition = ScriptDefinitionsManager.getInstance(project).getDefinitions().first()

      val settings = KotlinScriptingSettingsImpl.getInstance(project)

      val initialIsEnabled = settings.isScriptDefinitionEnabled(scriptDefinition)

      settings.setEnabled(scriptDefinition, !initialIsEnabled)

      assertEquals(
        "scriptDefinition should be off",
        "false",
        XMLOutputter().outputString(settings.state)
          .substringAfter("<isEnabled>")
          .substringBefore("</isEnabled>")
      )

      settings.setEnabled(scriptDefinition, initialIsEnabled)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinLightProjectDescriptor.INSTANCE
    }
}
