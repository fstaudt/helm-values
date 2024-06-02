package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.settings.model.ChartRepositoryState
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry

class ChartRepositoryServiceTest : BasePlatformTestCase() {
    private lateinit var state: HelmValuesSettings
    private lateinit var passwordSafe: PasswordSafe
    private lateinit var service: ChartRepositoryService

    companion object {
        private const val APPS = "apps"
        private const val APPS_URL = "https://nexus/apps"
        private const val BUNDLES = "bundles"
        private const val BUNDLES_URL = "https://nexus/bundles"
        private const val USERNAME = "user"
        private const val PASSWORD = "passwd"
    }

    private fun reset() {
        state = HelmValuesSettings.instance.state
        state.reset()
        passwordSafe = PasswordSafe.instance
        passwordSafe.set(credentialsFor(APPS), null)
        passwordSafe.set(credentialsFor(BUNDLES), null)
        service = ChartRepositoryService.instance
        mockkConstructor(GeneralCommandLine::class)
        every { anyConstructed<GeneralCommandLine>().createProcess() } returns mockk<Process>(relaxed = true)
        mockkConstructor(OSProcessHandler::class)
        every { anyConstructed<OSProcessHandler>().waitFor(any()) } returns true
        every { anyConstructed<OSProcessHandler>().exitCode } returns 0
    }

    fun `test - list should return empty list by default`() {
        reset()
        assertThat(service.list()).isEmpty()
    }

    fun `test - list should return all repositories from state`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositoryState(APPS_URL, pushedToHelm = true),
            BUNDLES to ChartRepositoryState(BUNDLES_URL, pushedToHelm = false),
        )
        assertThat(service.list()).containsExactly(
            ChartRepository(APPS, APPS_URL, pushedToHelm = true),
            ChartRepository(BUNDLES, BUNDLES_URL, pushedToHelm = false),
        )
    }

    fun `test - list should retrieve credentials in password safe for all repositories from state`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositoryState(APPS_URL),
            BUNDLES to ChartRepositoryState(BUNDLES_URL),
        )
        passwordSafe.set(credentialsFor(APPS), Credentials(USERNAME, PASSWORD))
        assertThat(service.list()).containsExactly(
            ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD, true),
            ChartRepository(BUNDLES, BUNDLES_URL, pushedToHelm = true),
        )
    }

    fun `test - list should retrieve credentials from reference repository when it is provided`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositoryState(APPS_URL),
            BUNDLES to ChartRepositoryState(BUNDLES_URL, APPS),
        )
        passwordSafe.set(credentialsFor(APPS), Credentials(USERNAME, PASSWORD))
        assertThat(service.list()).containsExactly(
            ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD, true),
            ChartRepository(BUNDLES, BUNDLES_URL, APPS, USERNAME, PASSWORD, true),
        )
    }

    fun `test - list should not set credentials when reference repository is not found`() {
        reset()
        state.chartRepositories = mapOf(
            BUNDLES to ChartRepositoryState(BUNDLES_URL, APPS),
        )
        assertThat(service.list()).containsExactly(
            ChartRepository(BUNDLES, BUNDLES_URL, APPS, pushedToHelm = true),
        )
    }

    fun `test - list should retrieve JSON schema repository mappings from state`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositoryState(APPS_URL),
            BUNDLES to ChartRepositoryState(BUNDLES_URL),
        )
        state.jsonSchemaRepositories = mapOf(
            "@$APPS" to JsonSchemaRepositoryState("$APPS_URL/json-schemas"),
            "other" to JsonSchemaRepositoryState("$BUNDLES_URL/json-schemas"),
        )
        assertThat(service.list()).containsExactly(
            ChartRepository(APPS, APPS_URL, pushedToHelm = true,
                jsonSchemaRepositoryMapping = JsonSchemaRepositoryMapping("@$APPS", "$APPS_URL/json-schemas")),
            ChartRepository(BUNDLES, BUNDLES_URL, pushedToHelm = true)
        )
    }

    fun `test - update should put all repositories in state`() {
        reset()
        service.update(project, listOf(ChartRepository(APPS, APPS_URL), ChartRepository(BUNDLES, BUNDLES_URL)))
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL)),
            entry(BUNDLES, ChartRepositoryState(BUNDLES_URL)),
        )
    }

    fun `test - update should overwrite existing repositories in state`() {
        reset()
        state.chartRepositories = mapOf(BUNDLES to ChartRepositoryState("https://nexus/previous"))
        service.update(project, listOf(ChartRepository(BUNDLES, BUNDLES_URL)))
        assertThat(state.chartRepositories).containsExactly(
            entry(BUNDLES, ChartRepositoryState(BUNDLES_URL))
        )
    }

    fun `test - update should put passwords in password safe`() {
        reset()
        service.update(project,
            listOf(
                ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD),
                ChartRepository(BUNDLES, BUNDLES_URL),
            )
        )
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL)),
            entry(BUNDLES, ChartRepositoryState(BUNDLES_URL)),
        )
        assertThat(passwordSafe.get(credentialsFor(APPS))).isEqualTo(Credentials(USERNAME, PASSWORD))
        assertThat(passwordSafe.get(credentialsFor(BUNDLES))).isNull()
    }

    fun `test - update should clear credentials of removed repositories from password safe`() {
        reset()
        state.chartRepositories = mapOf(BUNDLES to ChartRepositoryState(BUNDLES_URL))
        passwordSafe.set(credentialsFor(BUNDLES), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(ChartRepository(APPS, APPS_URL)))
        assertThat(state.chartRepositories).containsExactly(entry(APPS, ChartRepositoryState(APPS_URL)))
        assertThat(state.chartRepositories).doesNotContainKey(BUNDLES)
        assertThat(passwordSafe.get(credentialsFor(BUNDLES))).isNull()
    }

    fun `test - update should clear credentials when reference repository is provided`() {
        reset()
        state.chartRepositories = mapOf(BUNDLES to ChartRepositoryState(BUNDLES_URL))
        passwordSafe.set(credentialsFor(BUNDLES), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(
            ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD),
            ChartRepository(BUNDLES, BUNDLES_URL, APPS, USERNAME, PASSWORD),
        ))
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL)),
            entry(BUNDLES, ChartRepositoryState(BUNDLES_URL, APPS))
        )
        assertThat(passwordSafe.get(credentialsFor(APPS))).isEqualTo(Credentials(USERNAME, PASSWORD))
        assertThat(passwordSafe.get(credentialsFor(BUNDLES))).isNull()
    }

    fun `test - update should clear credentials of unsecure repositories from password safe`() {
        reset()
        state.chartRepositories = mapOf(BUNDLES to ChartRepositoryState(BUNDLES_URL))
        passwordSafe.set(credentialsFor(BUNDLES), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(ChartRepository(BUNDLES, BUNDLES_URL)))
        assertThat(state.chartRepositories).containsExactly(entry(BUNDLES, ChartRepositoryState(BUNDLES_URL)))
        assertThat(passwordSafe.get(credentialsFor(BUNDLES))).isNull()
    }

    fun `test - update should call helm repo add for new repository and mark repository as pushed`() {
        reset()
        service.update(project, listOf(ChartRepository(APPS, APPS_URL)))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--force-update"))
        }
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true))
        )
    }

    fun `test - update should call helm repo add with credentials for new repository with credentials`() {
        reset()
        service.update(project, listOf(ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD)))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--username", USERNAME,
                "--password", PASSWORD,
                "--force-update"))
        }
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true))
        )
    }

    fun `test - update should call helm repo add for repository with updated URL`() {
        reset()
        state.chartRepositories = mapOf(APPS to ChartRepositoryState("https://nexus/previous"))
        service.update(project, listOf(ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD)))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--username", USERNAME,
                "--password", PASSWORD,
                "--force-update"))
        }
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true))
        )
    }

    fun `test - update should call helm repo add for repository with updated credentials`() {
        reset()
        state.chartRepositories = mapOf(APPS to ChartRepositoryState(APPS_URL))
        passwordSafe.set(credentialsFor(APPS), Credentials(USERNAME, "previous"))
        service.update(project, listOf(ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD)))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--username", USERNAME,
                "--password", PASSWORD,
                "--force-update"))
        }
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true))
        )
    }

    fun `test - update should call helm repo add for referenced repository with updated credentials`() {
        reset()
        state.chartRepositories = mapOf(
            APPS to ChartRepositoryState(APPS_URL),
            BUNDLES to ChartRepositoryState(BUNDLES_URL, APPS)
        )
        passwordSafe.set(credentialsFor(APPS), Credentials(USERNAME, "previous"))
        service.update(project, listOf(
            ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD),
            ChartRepository(BUNDLES, BUNDLES_URL, APPS, USERNAME, PASSWORD)
        ))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--username", USERNAME,
                "--password", PASSWORD,
                "--force-update"))
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", BUNDLES, BUNDLES_URL,
                "--username", USERNAME,
                "--password", PASSWORD,
                "--force-update"))
        }
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true)),
            entry(BUNDLES, ChartRepositoryState(BUNDLES_URL, APPS, pushedToHelm = true))
        )
    }

    fun `test - update should mark repository as not pushed when helm repo add fails`() {
        reset()
        every { anyConstructed<OSProcessHandler>().exitCode } returns 1
        service.update(project, listOf(ChartRepository(APPS, APPS_URL)))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf(
                "repo", "add", APPS, APPS_URL,
                "--force-update"))
        }
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = false))
        )
    }

    fun `test - update should NOT call helm repo add for unchanged repositories`() {
        reset()
        every { anyConstructed<OSProcessHandler>().exitCode } returns 1
        state.chartRepositories = mapOf(APPS to ChartRepositoryState(APPS_URL))
        service.update(project, listOf(ChartRepository(APPS, APPS_URL, pushedToHelm = true)))
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true)),
        )
    }

    fun `test - update should NOT call helm repo add for unchanged secure repositories`() {
        reset()
        every { anyConstructed<OSProcessHandler>().exitCode } returns 1
        state.chartRepositories = mapOf(APPS to ChartRepositoryState(APPS_URL))
        passwordSafe.set(credentialsFor(APPS), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD, true)))
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true))
        )
    }

    fun `test - update should call NOT helm repo add for unchanged secure referenced repositories`() {
        reset()
        every { anyConstructed<OSProcessHandler>().exitCode } returns 1
        state.chartRepositories = mapOf(
            APPS to ChartRepositoryState(APPS_URL),
            BUNDLES to ChartRepositoryState(BUNDLES_URL, APPS)
        )
        passwordSafe.set(credentialsFor(APPS), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(
            ChartRepository(APPS, APPS_URL, "", USERNAME, PASSWORD, true),
            ChartRepository(BUNDLES, BUNDLES_URL, APPS, USERNAME, PASSWORD, true)
        ))
        assertThat(state.chartRepositories).containsExactly(
            entry(APPS, ChartRepositoryState(APPS_URL, pushedToHelm = true)),
            entry(BUNDLES, ChartRepositoryState(BUNDLES_URL, APPS, pushedToHelm = true))
        )
    }

    fun `test - update should call helm repo remove for removed repositories`() {
        reset()
        state.chartRepositories = mapOf(BUNDLES to ChartRepositoryState(APPS_URL))
        service.update(project, listOf(ChartRepository(APPS, APPS_URL)))
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withExePath("helm")
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("repo", "remove", BUNDLES))
        }
    }

    private fun credentialsFor(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName("HelmValues.helmRepositories", key))
    }
}
