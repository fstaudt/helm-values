package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import io.github.fstaudt.helm.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepository
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping

class JsonSchemaRepositoryMappingService {
    companion object {
        val instance: JsonSchemaRepositoryMappingService =
            ApplicationManager.getApplication().getService(JsonSchemaRepositoryMappingService::class.java)
    }

    private val state = HelmValuesSettings.instance.state
    private val passwordSafe = PasswordSafe.instance

    fun list(): List<JsonSchemaRepositoryMapping> {
        return state.jsonSchemaRepositories.map {
            val credentials = passwordSafe.get(credentialAttributes(it.key))
            it.value.toJsonSchemaRepositoryMapping(it.key, credentials)
        }
    }

    fun update(items: List<JsonSchemaRepositoryMapping>) {
        state.jsonSchemaRepositories.forEach { r ->
            if (items.none { r.key == it.name && it.secured() }) {
                passwordSafe.set(credentialAttributes(r.key), null)
            }
        }
        items.forEach {
            if (it.secured()) {
                passwordSafe.set(credentialAttributes(it.name), Credentials(it.username, it.password))
            }
        }
        state.jsonSchemaRepositories = items.associateBy { it.name }.mapValues { it.value.toJsonSchemaRepository() }
    }

    private fun JsonSchemaRepository.toJsonSchemaRepositoryMapping(
        name: String,
        credentials: Credentials?
    ): JsonSchemaRepositoryMapping {
        return JsonSchemaRepositoryMapping(
            name,
            baseUri,
            credentials?.userName.orEmpty(),
            credentials?.password?.toString().orEmpty(),
            valuesSchemaFile,
            globalValuesSchemaFile
        )
    }

    private fun JsonSchemaRepositoryMapping.toJsonSchemaRepository(): JsonSchemaRepository {
        return JsonSchemaRepository(
            baseUri,
            valuesSchemaFile.orElse(VALUES_SCHEMA_FILE),
            globalValuesSchemaFile.orElse(GLOBAL_VALUES_SCHEMA_FILE),
        )
    }

    private fun String.orElse(default: String) = takeUnless { it.isBlank() } ?: default

    private fun credentialAttributes(key: String) = CredentialAttributes(generateServiceName("HelmValues", key))
}