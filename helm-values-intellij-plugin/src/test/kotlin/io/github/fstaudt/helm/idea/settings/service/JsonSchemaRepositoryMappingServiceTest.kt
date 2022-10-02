package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepository
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import org.assertj.core.api.Assertions.assertThat
import java.util.AbstractMap.SimpleEntry

class JsonSchemaRepositoryMappingServiceTest : BasePlatformTestCase() {
    private lateinit var state: HelmValuesSettings
    private lateinit var passwordSafe: PasswordSafe
    private lateinit var service: JsonSchemaRepositoryMappingService

    private fun reset() {
        state = HelmValuesSettings.instance.state
        state.jsonSchemaRepositories = emptyMap()
        passwordSafe = PasswordSafe.instance
        passwordSafe.set(credentialAttributes("@apps"), null)
        passwordSafe.set(credentialAttributes("@bundles"), null)
        service = JsonSchemaRepositoryMappingService.instance
    }

    fun `test - jsonSchemaRepositoryMappings should return empty mappings by default`() {
        reset()
        assertThat(service.list()).isEmpty()
    }

    fun `test - jsonSchemaRepositoryMappings should return all mappings from state`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            "@apps" to JsonSchemaRepository("https://nexus/apps"),
            "@bundles" to JsonSchemaRepository("https://nexus/bundles"),
        )
        assertThat(service.list()).containsExactlyInAnyOrder(
            JsonSchemaRepositoryMapping("@apps", "https://nexus/apps"),
            JsonSchemaRepositoryMapping("@bundles", "https://nexus/bundles"),
        )
    }

    fun `test - jsonSchemaRepositoryMappings should retrieve passwords in password safe for all mappings from state`() {
        reset()
        state.jsonSchemaRepositories = mapOf(
            "@apps" to JsonSchemaRepository("https://nexus/apps"),
            "@bundles" to JsonSchemaRepository("https://nexus/bundles"),
        )
        passwordSafe.set(credentialAttributes("@apps"), Credentials("user", "password"))
        assertThat(service.list()).containsExactlyInAnyOrder(
            JsonSchemaRepositoryMapping("@apps", "https://nexus/apps", "user", "password"),
            JsonSchemaRepositoryMapping("@bundles", "https://nexus/bundles"),
        )
    }

    fun `test - update should put all mappings in state`() {
        reset()
        service.update(
            listOf(
                JsonSchemaRepositoryMapping("@apps", "https://nexus/apps"),
                JsonSchemaRepositoryMapping("@bundles", "https://nexus/bundles"),
            )
        )
        assertThat(state.jsonSchemaRepositories).containsExactly(
            SimpleEntry("@apps", JsonSchemaRepository("https://nexus/apps")),
            SimpleEntry("@bundles", JsonSchemaRepository("https://nexus/bundles")),
        )
    }

    fun `test - update should overwrite existing mapping in state`() {
        reset()
        state.jsonSchemaRepositories = mapOf("@bundles" to JsonSchemaRepository("https://nexus/previous"))
        service.update(
            listOf(
                JsonSchemaRepositoryMapping(
                    "@bundles",
                    "https://nexus/bundles",
                    "",
                    "",
                    "helm-values.json",
                    "helm-global.json"
                )
            )
        )
        assertThat(state.jsonSchemaRepositories).containsExactly(
            SimpleEntry(
                "@bundles",
                JsonSchemaRepository("https://nexus/bundles", "helm-values.json", "helm-global.json")
            )
        )
    }

    fun `test - update should put passwords in password safe`() {
        reset()
        service.update(
            listOf(
                JsonSchemaRepositoryMapping("@apps", "https://nexus/apps", "user", "password"),
                JsonSchemaRepositoryMapping("@bundles", "https://nexus/bundles"),
            )
        )
        assertThat(state.jsonSchemaRepositories).containsExactly(
            SimpleEntry("@apps", JsonSchemaRepository("https://nexus/apps")),
            SimpleEntry("@bundles", JsonSchemaRepository("https://nexus/bundles")),
        )
        assertThat(passwordSafe.get(credentialAttributes("@apps"))).isEqualTo(Credentials("user", "password"))
        assertThat(passwordSafe.get(credentialAttributes("@bundles"))).isNull()
    }

    fun `test - update should clear passwords of removed mappings from password safe`() {
        reset()
        state.jsonSchemaRepositories = mapOf("@bundles" to JsonSchemaRepository("https://nexus/apps"))
        passwordSafe.set(credentialAttributes("@bundles"), Credentials("user", "password"))
        service.update(listOf(JsonSchemaRepositoryMapping("@apps", "https://nexus/apps")))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            SimpleEntry("@apps", JsonSchemaRepository("https://nexus/apps"))
        )
        assertThat(state.jsonSchemaRepositories).doesNotContainKey("@bundles")
        assertThat(passwordSafe.get(credentialAttributes("@bundles"))).isNull()
    }

    fun `test - update should clear passwords of unsecure mappings from password safe`() {
        reset()
        state.jsonSchemaRepositories = mapOf("@bundles" to JsonSchemaRepository("https://nexus/bundles"))
        passwordSafe.set(credentialAttributes("@bundles"), Credentials("user", "password"))
        service.update(listOf(JsonSchemaRepositoryMapping("@bundles", "https://nexus/bundles")))
        assertThat(state.jsonSchemaRepositories).containsExactly(
            SimpleEntry("@bundles", JsonSchemaRepository("https://nexus/bundles"))
        )
        assertThat(passwordSafe.get(credentialAttributes("@bundles"))).isNull()
    }

    private fun credentialAttributes(key: String) = CredentialAttributes(generateServiceName("HelmValues", key))
}
