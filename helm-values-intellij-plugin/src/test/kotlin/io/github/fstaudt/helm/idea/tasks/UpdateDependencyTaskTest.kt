package io.github.fstaudt.helm.idea.tasks

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.HeavyPlatformTestCase
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verifyOrder
import java.io.File

class UpdateDependencyTaskTest : HeavyPlatformTestCase() {
    private lateinit var state: HelmValuesSettings

    private fun reset() {
        state = HelmValuesSettings.instance.state
        state.helmBinaryPath = "helm"
        state.jsonSchemaRepositories = emptyMap()
        mockkConstructor(GeneralCommandLine::class)
        every { anyConstructed<GeneralCommandLine>().createProcess() } returns mockk<Process>(relaxed = true)
        mockkConstructor(OSProcessHandler::class)
        every { anyConstructed<OSProcessHandler>().waitFor(any()) } returns true
        every { anyConstructed<OSProcessHandler>().exitCode } returns 0
    }

    fun `test - run should update repositories and dependencies for provided chart`() {
        reset()
        val chartDir = File(project.baseDir(), CHART_NAME)
        project.initHelmChart(chartDir)
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        UpdateDependencyTask(project, File(chartDir, HELM_CHART_FILE)).also {
            it.run(indicator)
        }
        verifyOrder {
            indicator.text = eq("Updating dependencies for chart $CHART_NAME")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("repo", "update"))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), CHART_NAME))
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("dependency", "update", ".", "--skip-refresh"))
            anyConstructed<GeneralCommandLine>().createProcess()
        }
    }
}
