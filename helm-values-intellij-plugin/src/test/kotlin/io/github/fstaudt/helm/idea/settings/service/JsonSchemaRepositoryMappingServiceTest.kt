package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry

class JsonSchemaRepositoryMappingServiceTest : BasePlatformTestCase() {
    private lateinit var state: HelmValuesSettings
    private lateinit var passwordSafe: PasswordSafe
    private lateinit var service: JsonSchemaRepositoryMappingService

    companion object {
        private const val APPS = "@apps"
        private const val APPS_URI = "https://nexus/apps"
        private const val BUNDLES = "@bundles"
        private const val BUNDLES_URI = "https://nexus/bundles"
        private const val USERNAME = "user"
        private const val PASSWORD = "password"
        private const val VALUES_SCHEMA = "values.json"
        private const val GLOBAL_SCHEMA = "global.json"
    }

    private fun reset() {
        state = HelmValuesSettings.instance().state
        state.reset()
        passwordSafe = PasswordSafe.instance
        passwordSafe.set(credentialAttributes(APPS), null)
        passwordSafe.set(credentialAttributes(BUNDLES), null)
        service = JsonSchemaRepositoryMappingService.instance()
    }

    fun `test - jsonSchemaRepositoryMappings should return empty mappings by default`() {
        reset()
        assertThat(service.list()).isEmpty()
    }

    fun `test - jsonSchemaRepositoryMappings should return all mappings from state`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            APPS to JsonSchemaRepositoryState(APPS_URI),
            BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI),
        )
        assertThat(service.list()).containsExactly(
            JsonSchemaRepositoryMapping(APPS, APPS_URI),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI),
        )
    }

    fun `test - jsonSchemaRepositoryMappings should retrieve specific values files from state`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            APPS to JsonSchemaRepositoryState(APPS_URI, valuesSchemaFile = VALUES_SCHEMA),
            BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI, globalValuesSchemaFile = GLOBAL_SCHEMA),
        )
        assertThat(service.list()).containsExactly(
            JsonSchemaRepositoryMapping(APPS, APPS_URI, valuesSchemaFile = VALUES_SCHEMA),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI, globalValuesSchemaFile = GLOBAL_SCHEMA),
        )
    }

    fun `test - jsonSchemaRepositoryMappings should retrieve specific values files from reference repository mapping when it is provided`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            APPS to JsonSchemaRepositoryState(APPS_URI, "", VALUES_SCHEMA, GLOBAL_SCHEMA),
            BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI, APPS),
        )
        assertThat(service.list()).containsExactly(
            JsonSchemaRepositoryMapping(APPS, APPS_URI,
                valuesSchemaFile = VALUES_SCHEMA,
                globalValuesSchemaFile = GLOBAL_SCHEMA),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI, APPS,
                valuesSchemaFile = VALUES_SCHEMA,
                globalValuesSchemaFile = GLOBAL_SCHEMA),
        )
    }

    fun `test - jsonSchemaRepositoryMappings should retrieve credentials in password safe for all mappings from state`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            APPS to JsonSchemaRepositoryState(APPS_URI),
            BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI),
        )
        passwordSafe.set(credentialAttributes(APPS), Credentials(USERNAME, PASSWORD))
        assertThat(service.list()).containsExactly(
            JsonSchemaRepositoryMapping(APPS, APPS_URI, "", USERNAME, PASSWORD),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI),
        )
    }

    fun `test - jsonSchemaRepositoryMappings should retrieve credentials from reference repository when it is provided`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            APPS to JsonSchemaRepositoryState(APPS_URI),
            BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI, APPS),
        )
        passwordSafe.set(credentialAttributes(APPS), Credentials(USERNAME, PASSWORD))
        assertThat(service.list()).containsExactly(
            JsonSchemaRepositoryMapping(APPS, APPS_URI, "", USERNAME, PASSWORD),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI, APPS, USERNAME, PASSWORD),
        )
    }

    fun `test - jsonSchemaRepositoryMappings should use default configuration when reference repository mapping is not found`() {
        reset()
        state.jsonSchemaRepositories = mapOf(BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI, APPS))
        assertThat(service.list()).containsExactly(JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI, APPS))
    }

    fun `test - update should put all mappings in state`() {
        reset()
        service.update(project, listOf(
            JsonSchemaRepositoryMapping(APPS, APPS_URI),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI),
        ))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            entry(APPS, JsonSchemaRepositoryState(APPS_URI)),
            entry(BUNDLES, JsonSchemaRepositoryState(BUNDLES_URI)),
        )
    }

    fun `test - update should overwrite existing mapping in state`() {
        reset()
        state.jsonSchemaRepositories = mapOf(BUNDLES to JsonSchemaRepositoryState("https://nexus/previous"))
        service.update(project, listOf(
            JsonSchemaRepositoryMapping(
                BUNDLES,
                BUNDLES_URI,
                valuesSchemaFile = VALUES_SCHEMA,
                globalValuesSchemaFile = GLOBAL_SCHEMA
            )
        ))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            entry(BUNDLES, JsonSchemaRepositoryState(BUNDLES_URI, "", VALUES_SCHEMA, GLOBAL_SCHEMA))
        )
    }

    fun `test - update should only keep reference repository mapping in state when it is provided`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            BUNDLES to JsonSchemaRepositoryState("https://previous", "", VALUES_SCHEMA, GLOBAL_SCHEMA)
        )
        service.update(project, listOf(
            JsonSchemaRepositoryMapping(
                BUNDLES,
                BUNDLES_URI,
                APPS,
                "",
                "",
                VALUES_SCHEMA,
                GLOBAL_SCHEMA)
        ))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            entry(BUNDLES, JsonSchemaRepositoryState(BUNDLES_URI, APPS))
        )
    }

    fun `test - update should put passwords in password safe`() {
        reset()
        service.update(project,
            listOf(
                JsonSchemaRepositoryMapping(APPS, APPS_URI, "", USERNAME, PASSWORD),
                JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI),
            )
        )
        assertThat(state.jsonSchemaRepositories).containsExactly(
            entry(APPS, JsonSchemaRepositoryState(APPS_URI)),
            entry(BUNDLES, JsonSchemaRepositoryState(BUNDLES_URI)),
        )
        assertThat(passwordSafe.get(credentialAttributes(APPS))).isEqualTo(Credentials(USERNAME, PASSWORD))
        assertThat(passwordSafe.get(credentialAttributes(BUNDLES))).isNull()
    }

    fun `test - update should clear credentials of removed mappings from password safe`() {
        reset()
        state.jsonSchemaRepositories = mapOf(BUNDLES to JsonSchemaRepositoryState(APPS_URI))
        passwordSafe.set(credentialAttributes(BUNDLES), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(JsonSchemaRepositoryMapping(APPS, APPS_URI)))
        assertThat(state.jsonSchemaRepositories).containsExactly(entry(APPS, JsonSchemaRepositoryState(APPS_URI)))
        assertThat(state.jsonSchemaRepositories).doesNotContainKey(BUNDLES)
        assertThat(passwordSafe.get(credentialAttributes(BUNDLES))).isNull()
    }

    fun `test - update should clear credentials of unsecure mappings from password safe`() {
        reset()
        state.jsonSchemaRepositories = mapOf(BUNDLES to JsonSchemaRepositoryState(BUNDLES_URI))
        passwordSafe.set(credentialAttributes(BUNDLES), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI)))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            entry(BUNDLES, JsonSchemaRepositoryState(BUNDLES_URI))
        )
        assertThat(passwordSafe.get(credentialAttributes(BUNDLES))).isNull()
    }

    fun `test - update should clear credentials when reference repository mapping is provided`() {
        reset()
        state.jsonSchemaRepositories = mapOf(BUNDLES to JsonSchemaRepositoryState("https://previous"))
        passwordSafe.set(credentialAttributes(BUNDLES), Credentials(USERNAME, PASSWORD))
        service.update(project, listOf(
            JsonSchemaRepositoryMapping(APPS, APPS_URI, "", USERNAME, PASSWORD),
            JsonSchemaRepositoryMapping(BUNDLES, BUNDLES_URI, APPS, USERNAME, PASSWORD)
        ))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            entry(APPS, JsonSchemaRepositoryState(APPS_URI)),
            entry(BUNDLES, JsonSchemaRepositoryState(BUNDLES_URI, APPS))
        )
        assertThat(passwordSafe.get(credentialAttributes(APPS))).isEqualTo(Credentials(USERNAME, PASSWORD))
        assertThat(passwordSafe.get(credentialAttributes(BUNDLES))).isNull()
    }

    private fun credentialAttributes(key: String) = CredentialAttributes(generateServiceName("HelmValues", key))
}
