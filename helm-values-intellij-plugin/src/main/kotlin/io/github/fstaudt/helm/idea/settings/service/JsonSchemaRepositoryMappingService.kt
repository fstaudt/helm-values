package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryState
import io.github.fstaudt.helm.idea.tasks.actions.PasswordSafeNotificationAction

@Service
class JsonSchemaRepositoryMappingService {
    companion object {
        private const val CREDENTIALS = "HelmValues"
        fun instance(): JsonSchemaRepositoryMappingService =
            ApplicationManager.getApplication().getService(JsonSchemaRepositoryMappingService::class.java)
    }

    fun list(): List<JsonSchemaRepositoryMapping> {
        val state = HelmValuesSettings.instance().state
        return state.jsonSchemaRepositories.map {
            it.value.toJsonSchemaRepositoryMapping(it.key, credentialsFor(it.key))
        }.also { list ->
            list.forEach { it.inheritConfigurationFromReferenceRepositoryIn(list) }
        }.sortedBy { it.name }
    }

    fun get(name: String): JsonSchemaRepositoryMapping? {
        return list().firstOrNull { it.name == name }
    }

    fun update(project: Project?, items: List<JsonSchemaRepositoryMapping>) {
        val state = HelmValuesSettings.instance().state
        val passwordSafe = PasswordSafe.instance
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
        if (passwordSafe.isMemoryOnly && items.any { it.secured() }) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("helm.values.errors")
                .createNotification(message("settings.mappings.readOnlyMemory.title"),
                    message("settings.password.warning"), WARNING)
                .apply { addAction(PasswordSafeNotificationAction()) }
                .notify(project)
        }
    }

    private fun JsonSchemaRepositoryState.toJsonSchemaRepositoryMapping(
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

    private fun JsonSchemaRepositoryMapping.toJsonSchemaRepository(): JsonSchemaRepositoryState {
        return JsonSchemaRepositoryState(
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
    private fun credentialsFor(key: String) = PasswordSafe.instance.get(credentialAttributesFor(key))
    private fun credentialAttributesFor(key: String) = CredentialAttributes(generateServiceName(CREDENTIALS, key))
}
