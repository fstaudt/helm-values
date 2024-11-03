package io.github.fstaudt.helm.idea.tasks

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.testFramework.HeavyPlatformTestCase
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.CHART_VERSION
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.File

class UpdateDependencyAllTaskTest : HeavyPlatformTestCase() {
    companion object {
        private const val OTHER_CHART_NAME = "other-chart"
        private const val SUB_CHART_NAME = "sub-chart"
    }

    private lateinit var state: HelmValuesSettings

    private fun reset() {
        state = HelmValuesSettings.instance().state
        state.reset()
        mockkConstructor(GeneralCommandLine::class)
        every { anyConstructed<GeneralCommandLine>().createProcess() } returns mockk<Process>(relaxed = true)
        mockkConstructor(OSProcessHandler::class)
        every { anyConstructed<OSProcessHandler>().waitFor(any()) } returns true
        every { anyConstructed<OSProcessHandler>().exitCode } returns 0
    }

    fun `test - run should update dependencies for all charts in project`() {
        reset()
        project.initHelmChart(File(project.baseDir(), CHART_NAME))
        project.initHelmChart(File(project.baseDir(), OTHER_CHART_NAME))
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        UpdateDependencyAllTask(project).also {
            it.run(indicator)
        }
        verifyOrder {
            indicator.isIndeterminate = false
            indicator.fraction = eq(0.0)
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), CHART_NAME))
            anyConstructed<GeneralCommandLine>().createProcess()
            indicator.fraction = eq(0.5)
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), OTHER_CHART_NAME))
            anyConstructed<GeneralCommandLine>().createProcess()
        }
    }

    fun `test - run should update dependencies with order depending on local chart dependencies`() {
        reset()
        project.initHelmChart(File(project.baseDir(), CHART_NAME)) {
            appendText("""
                dependencies:
                - name: $OTHER_CHART_NAME
                  version: $CHART_VERSION
                  repository: "file://../$OTHER_CHART_NAME"
            """.trimIndent())
        }
        project.initHelmChart(File(project.baseDir(), OTHER_CHART_NAME)) {
            appendText("""
                dependencies:
                - name: $OTHER_CHART_NAME
                  version: $CHART_VERSION
                  repository: "file://../$SUB_CHART_NAME"
            """.trimIndent())
        }
        project.initHelmChart(File(project.baseDir(), SUB_CHART_NAME))
        UpdateDependencyAllTask(project).also {
            it.run(BackgroundableProcessIndicator(it))
        }
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), SUB_CHART_NAME))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), OTHER_CHART_NAME))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), CHART_NAME))
            anyConstructed<GeneralCommandLine>().createProcess()
        }
    }

    fun `test - run should ignore charts in internal IntelliJ folder`() {
        reset()
        project.initHelmChart(File(project.baseDir(), ".idea/$CHART_NAME"))
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        UpdateDependencyAllTask(project).also {
            it.run(indicator)
        }
        verify(exactly = 0) { anyConstructed<GeneralCommandLine>().createProcess() }
    }

    fun `test - run should ignore charts in internal Gradle folder`() {
        reset()
        project.initHelmChart(File(project.baseDir(), "build/helm-values/$CHART_NAME"))
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        UpdateDependencyAllTask(project).also {
            it.run(indicator)
        }
        verify(exactly = 0) { anyConstructed<GeneralCommandLine>().createProcess() }
    }
}
