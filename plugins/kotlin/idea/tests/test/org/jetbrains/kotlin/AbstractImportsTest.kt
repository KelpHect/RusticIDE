// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntry
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.util.ClassImportFilter
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractImportsTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected var userNotificationInfo: String? = null

    private fun findAfterFile(testPath: File): File {
        val regularAfterFile = testPath.resolveSibling(testPath.name + ".after")

        if (pluginMode == KotlinPluginMode.K2) {
            val k2AfterFileName = IgnoreTests.deriveK2FileName(regularAfterFile.name, IgnoreTests.FileExtension.K2)
            val k2AfterFile = testPath.resolveSibling(k2AfterFileName)

            if (k2AfterFile.exists()) return k2AfterFile
        }

        return regularAfterFile
    }

    protected open fun doTest(unused: String) {
        val testPath = dataFilePath(fileName())
        configureCodeStyleAndRun(project) {
            val fixture = myFixture
            val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")
            for (suffix in dependencySuffixes) {
                val dependencyPath = fileName().replace(".kt", suffix)
                if (File(testDataDirectory, dependencyPath).exists()) {
                    fixture.configureByFile(dependencyPath)
                }
            }

            fixture.configureByFile(fileName())

            val file = fixture.file as KtFile

            if (file.isScript()) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(file)
            }

            val fileText = file.text
            val codeStyleSettings = file.kotlinCustomSettings
            codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT = InTextDirectivesUtils.getPrefixedInt(
                fileText,
                "// NAME_COUNT_TO_USE_STAR_IMPORT:"
            ) ?: nameCountToUseStarImportDefault

            codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS = InTextDirectivesUtils.getPrefixedInt(
                fileText,
                "// NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS:"
            ) ?: nameCountToUseStarImportForMembersDefault

            codeStyleSettings.IMPORT_NESTED_CLASSES = InTextDirectivesUtils.getPrefixedBoolean(
                fileText,
                "// IMPORT_NESTED_CLASSES:"
            ) ?: false

            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PACKAGE_TO_USE_STAR_IMPORTS:").forEach {
                codeStyleSettings.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry(it.trim(), false))
            }

            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PACKAGES_TO_USE_STAR_IMPORTS:").forEach {
                codeStyleSettings.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry(it.trim(), true))
            }

            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// CLASS_IMPORT_FILTER_VETO_REGEX:").forEach {
                val regex = Regex(".*${it.trim()}.*")
                val filterExtension = ClassImportFilter { classInfo, _ -> !classInfo.fqName.asString().matches(regex) }
                ClassImportFilter.EP_NAME.point.registerExtension(filterExtension, testRootDisposable)
            }

            val log = if (runTestInWriteCommand) {
                project.executeWriteCommand<String?>("") { doTest(file) }
            } else {
                doTest(file)
            }

            val afterFile = findAfterFile(File(testPath))
            KotlinTestUtils.assertEqualsToFile(afterFile, myFixture.file.text)
            if (log != null) {
                val logFile = File("$testPath.log")
                if (log.isNotEmpty()) {
                    KotlinTestUtils.assertEqualsToFile(logFile, log)
                } else {
                    TestCase.assertFalse(logFile.exists())
                }
            }

            val message = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// WITH_MESSAGE: ")
            if (message != null) {
                assertNotNull("No user notification info was provided", userNotificationInfo)
                assertEquals(message, userNotificationInfo)
            }

            // Make sure that PSI is modified 
            // correctly and consistently during tests 
            PsiTestUtil.checkStubsMatchText(file)
        }
    }

    // returns test log
    protected abstract fun doTest(file: KtFile): String?

    protected open val nameCountToUseStarImportDefault: Int
        get() = 1

    protected open val nameCountToUseStarImportForMembersDefault: Int
        get() = 3

    protected open val runTestInWriteCommand: Boolean = true
}