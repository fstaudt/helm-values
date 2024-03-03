package io.github.fstaudt.helm.idea.tasks

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.HeavyPlatformTestCase
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.ChartRepositorySetting
import io.github.fstaudt.helm.idea.settings.service.ChartRepositoryServiceTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat

class AddRepositoryAllTaskTest : HeavyPlatformTestCase() {
    companion object {
        private const val APPS = "apps"
        private const val APPS_URL = "https://nexus/apps"
        private const val BUNDLES = "bundles"
        private const val BUNDLES_URL = "https://nexus/bundles"
    }

    private lateinit var state: HelmValuesSettings
    private lateinit var passwordSafe: PasswordSafe

    private fun reset() {
        state = HelmValuesSettings.instance.state
        state.reset()
        passwordSafe = PasswordSafe.instance
        passwordSafe.set(credentialsFor(APPS), null)
        passwordSafe.set(credentialsFor(BUNDLES), null)
        mockkConstructor(GeneralCommandLine::class)
        every { anyConstructed<GeneralCommandLine>().createProcess() } returns mockk<Process>(relaxed = true)
        mockkConstructor(OSProcessHandler::class)
        every { anyConstructed<OSProcessHandler>().waitFor(any()) } returns true
        every { anyConstructed<OSProcessHandler>().exitCode } returns 0
    }

    fun `test - run should synchronize all repositories configured in settings`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositorySetting(APPS_URL, synchronized = true),
            BUNDLES to ChartRepositorySetting(BUNDLES_URL, synchronized = false)
        )
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        AddRepositoryAllTask(project).also {
            it.run(indicator)
        }
        verifyOrder {
            indicator.isIndeterminate = false
            indicator.fraction = eq(0.0)
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters("repo", "add", APPS, APPS_URL, "--force-update")
            anyConstructed<GeneralCommandLine>().createProcess()
            indicator.fraction = eq(0.5)
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters("repo", "add", BUNDLES, BUNDLES_URL, "--force-update")
            anyConstructed<GeneralCommandLine>().createProcess()
        }
    }

    fun `test - run should update synchronized status when synchronization succeeds`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositorySetting(APPS_URL, synchronized = false),
        )
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        AddRepositoryAllTask(project).also {
            it.run(indicator)
        }
        assertThat(state.chartRepositories[APPS]?.synchronized).isTrue
    }

    fun `test - run should update synchronized status when synchronization fails`() {
        reset()
        every { anyConstructed<OSProcessHandler>().exitCode } returns 1
        state.chartRepositories = mapOf(
            APPS to ChartRepositorySetting(APPS_URL, synchronized = true),
        )
        val indicator = mockk<ProgressIndicator>(relaxed = true)
        AddRepositoryAllTask(project).also {
            it.run(indicator)
        }
        assertThat(state.chartRepositories[APPS]?.synchronized).isFalse
    }

    private fun credentialsFor(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName("HelmValues.helmRepositories", key))
    }
}
