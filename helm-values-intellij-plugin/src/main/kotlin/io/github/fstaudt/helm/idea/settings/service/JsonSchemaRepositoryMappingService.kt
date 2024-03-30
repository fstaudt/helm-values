package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepository
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping

@Service
class JsonSchemaRepositoryMappingService {
    companion object {
        private const val CREDENTIALS = "HelmValues"
        val instance: JsonSchemaRepositoryMappingService =
            ApplicationManager.getApplication().getService(JsonSchemaRepositoryMappingService::class.java)
    }

    private val state = HelmValuesSettings.instance.state
    private val passwordSafe = PasswordSafe.instance

    fun list(): List<JsonSchemaRepositoryMapping> {
        return state.jsonSchemaRepositories.map {
            it.value.toJsonSchemaRepositoryMapping(it.key, credentialsFor(it.key))
        }.also { list ->
            list.forEach { it.inheritConfigurationFromReferenceRepositoryIn(list) }
        }.sortedBy { it.name }
    }

    fun update(items: List<JsonSchemaRepositoryMapping>) {
        state.jsonSchemaRepositories.forEach { r ->
            if (items.none { r.key == it.name && it.secured() && !it.referenced() }) {
                passwordSafe.set(credentialAttributesFor(r.key), null)
            }
        }
        items.forEach {
            if (it.secured() && !it.referenced()) {
                passwordSafe.set(credentialAttributesFor(it.name), it.credentials())
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
            referenceRepositoryMapping,
            credentials?.userName.orEmpty(),
            credentials?.password?.toString().orEmpty(),
            valuesSchemaFile,
            globalValuesSchemaFile
        )
    }

    private fun JsonSchemaRepositoryMapping.toJsonSchemaRepository(): JsonSchemaRepository {
        return JsonSchemaRepository(
            baseUri,
            referenceRepositoryMapping,
            valuesSchemaFile.takeUnless { referenced() }.orElse(VALUES_SCHEMA_FILE),
            globalValuesSchemaFile.takeUnless { referenced() }.orElse(GLOBAL_VALUES_SCHEMA_FILE)
        )
    }

    private fun JsonSchemaRepositoryMapping.inheritConfigurationFromReferenceRepositoryIn(mappings: List<JsonSchemaRepositoryMapping>) {
        mappings.firstOrNull { it.name == referenceRepositoryMapping }?.let {
            username = it.username
            password = it.password
            valuesSchemaFile = it.valuesSchemaFile
            globalValuesSchemaFile = it.globalValuesSchemaFile
        }
    }

    private fun String?.orElse(default: String) = takeUnless { it.isNullOrBlank() } ?: default
    private fun credentialsFor(key: String) = passwordSafe.get(credentialAttributesFor(key))
    private fun credentialAttributesFor(key: String) = CredentialAttributes(generateServiceName(CREDENTIALS, key))
}
