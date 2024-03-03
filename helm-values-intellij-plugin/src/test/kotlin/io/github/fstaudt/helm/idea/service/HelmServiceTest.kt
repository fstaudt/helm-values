package io.github.fstaudt.helm.idea.service

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.testFramework.HeavyPlatformTestCase
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.CHART_VERSION
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.File

class HelmServiceTest : HeavyPlatformTestCase() {
    companion object {
        private const val OTHER_CHART_NAME = "other-chart"
        private const val SUB_CHART_NAME = "sub-chart"
        private const val APPS = "apps"
        private const val APPS_URL = "https://nexus/apps"
        private const val USERNAME = "user"
        private const val PASSWORD = "passwd"
    }

    private lateinit var state: HelmValuesSettings
    private lateinit var service: HelmService

    private fun reset() {
        state = HelmValuesSettings.instance.state
        state.reset()
        service = HelmService.instance
        mockkConstructor(GeneralCommandLine::class)
        every { anyConstructed<GeneralCommandLine>().createProcess() } returns mockk<Process>(relaxed = true)
        mockkConstructor(OSProcessHandler::class)
        every { anyConstructed<OSProcessHandler>().waitFor(any()) } returns true
        every { anyConstructed<OSProcessHandler>().exitCode } returns 0
    }

    fun `test - updateRepositories should call helm repo update`() {
        reset()
        service.updateRepositories(project)
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(project.baseDir())
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("repo", "update"))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<OSProcessHandler>().waitFor(any())
            anyConstructed<OSProcessHandler>().exitCode
        }
    }

    fun `test - updateRepositories should get helm binary from settings`() {
        reset()
        state.helmBinaryPath = "/custom/path/to/helm"
        service.updateRepositories(project)
        verify { anyConstructed<GeneralCommandLine>().withExePath("/custom/path/to/helm") }
    }

    fun `test - updateDependencies should update dependencies and skip refresh of repositories for provided chart`() {
        reset()
        val chartDir = File(project.baseDir(), CHART_NAME)
        project.initHelmChart(chartDir)
        service.updateDependencies(File(chartDir, HELM_CHART_FILE))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(chartDir)
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("dependency", "update", ".", "--skip-refresh"))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<OSProcessHandler>().waitFor(any())
            anyConstructed<OSProcessHandler>().exitCode
        }
    }

    fun `test - updateDependencies should update dependencies for provided chart and its local dependencies`() {
        reset()
        val chartDir = File(project.baseDir(), CHART_NAME)
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
        service.updateDependencies(File(chartDir, HELM_CHART_FILE))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), SUB_CHART_NAME))
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), OTHER_CHART_NAME))
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), CHART_NAME))
        }
    }

    fun `test - updateDependencies should skip invalid local dependencies`() {
        reset()
        val chartDir = File(project.baseDir(), CHART_NAME)
        project.initHelmChart(File(project.baseDir(), CHART_NAME)) {
            appendText("""
                dependencies:
                - name: invalid
                  version: $CHART_VERSION
                  repository: "file://../invalid"
            """.trimIndent())
        }
        service.updateDependencies(File(chartDir, HELM_CHART_FILE))
        verify {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), CHART_NAME))
        }
    }

    fun `test - updateDependencies should update dependencies for provided chart only when updateLocalDependencies is disabled`() {
        reset()
        val chartDir = File(project.baseDir(), CHART_NAME)
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
        service.updateDependencies(File(chartDir, HELM_CHART_FILE), false)
        verify {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), CHART_NAME))
        }
        verify(inverse = true) {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), SUB_CHART_NAME))
            anyConstructed<GeneralCommandLine>().withWorkDirectory(File(project.baseDir(), OTHER_CHART_NAME))
        }
    }

    fun `test - updateDependencies should get helm binary from settings`() {
        reset()
        val chartDir = File(project.baseDir(), CHART_NAME)
        project.initHelmChart(chartDir)
        state.helmBinaryPath = "/custom/path/to/helm"
        service.updateDependencies(File(chartDir, HELM_CHART_FILE))
        verify { anyConstructed<GeneralCommandLine>().withExePath("/custom/path/to/helm") }
    }

    fun `test - addRepository should call helm repo add with forced update`() {
        reset()
        service.addRepository(ChartRepository(APPS, APPS_URL))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--force-update"))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<OSProcessHandler>().waitFor(any())
            anyConstructed<OSProcessHandler>().exitCode
        }
    }

    fun `test - addRepository should call helm repo add with forced update and credentials`() {
        reset()
        service.addRepository(ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--username", "$USERNAME",
                "--password", "$PASSWORD",
                "--force-update"
            ))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<OSProcessHandler>().waitFor(any())
            anyConstructed<OSProcessHandler>().exitCode
        }
    }

    fun `test - addRepository should get helm binary from settings`() {
        reset()
        state.helmBinaryPath = "/custom/path/to/helm"
        service.addRepository(ChartRepository(APPS, APPS_URL))
        verify { anyConstructed<GeneralCommandLine>().withExePath("/custom/path/to/helm") }
    }

    fun `test - removeRepository should call helm repo remove`() {
        reset()
        service.removeRepository(ChartRepository(APPS))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("repo", "remove", APPS))
            anyConstructed<GeneralCommandLine>().createProcess()
            anyConstructed<OSProcessHandler>().waitFor(any())
            anyConstructed<OSProcessHandler>().exitCode
        }
    }

    fun `test - removeRepository should get helm binary from settings`() {
        reset()
        state.helmBinaryPath = "/custom/path/to/helm"
        service.removeRepository(ChartRepository(APPS))
        verify { anyConstructed<GeneralCommandLine>().withExePath("/custom/path/to/helm") }
    }
}
