package io.github.fstaudt.helm.idea.settings.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.settings.model.ChartRepositoryState
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.tasks.AddRepositoryTask
import io.github.fstaudt.helm.idea.tasks.RemoveRepositoryTask

@Service
class ChartRepositoryService {
    companion object {
        private const val CREDENTIALS = "HelmValues.helmRepositories"
        fun instance(): ChartRepositoryService =
            ApplicationManager.getApplication().getService(ChartRepositoryService::class.java)
    }

    fun list(): List<ChartRepository> {
        val state = HelmValuesSettings.instance().state
        return state.chartRepositories.map {
            it.value.toChartRepository(it.key, credentialsFor(it.key))
        }.also { list ->
            list.forEach { it.inheritCredentialsFromReferenceRepositoryIn(list) }
        }.sortedBy { it.name }
    }

    fun update(project: Project?, items: List<ChartRepository>) {
        val state = HelmValuesSettings.instance().state
        val passwordSafe = PasswordSafe.instance
        val progressManager = ProgressManager.getInstance()
        state.chartRepositories.forEach { (key, value) ->
            if (items.none { key == it.name } && value.pushedToHelm) {
                progressManager.run(RemoveRepositoryTask(project, value.toChartRepository(key)))
            }
            if (items.none { key == it.name && it.secured() && !it.referenced() }) {
                passwordSafe.set(credentialAttributesFor(key), null)
            }
        }
        items.forEach {
            if (it.secured() && !it.referenced() && credentialsFor(it.name) != it.credentials()) {
                it.pushedToHelm = false
            } else if (it.secured() && it.referenced() && credentialsFor(it.referenceRepository) != it.credentials()) {
                it.pushedToHelm = false
            } else if (it.url != state.chartRepositories[it.name]?.url) {
                it.pushedToHelm = false
            }
        }
        state.chartRepositories = items.associateBy { it.name }.mapValues { (_, it) ->
            ChartRepositoryState(it.url, it.referenceRepository)
        }
        items.forEach {
            if (it.secured() && !it.referenced()) {
                passwordSafe.set(credentialAttributesFor(it.name), it.credentials())
            }
            if (!it.pushedToHelm) {
                progressManager.runProcessWithProgressSynchronously(
                    AddRepositoryTask(project, it)::runSynchronously,
                    message("tasks.addRepository.title"), false, project)
            }
        }
    }

    private fun ChartRepositoryState.toChartRepository(
        name: String,
        credentials: Credentials? = null
    ): ChartRepository {
        val jsonSchemaRepositoryMappingService = JsonSchemaRepositoryMappingService.instance()
        return ChartRepository(
            name,
            url,
            referenceRepository,
            credentials?.userName.orEmpty(),
            credentials?.password?.toString().orEmpty(),
            pushedToHelm,
            jsonSchemaRepositoryMappingService.get("@$name") ?: JsonSchemaRepositoryMapping(),
        )
    }

    private fun ChartRepository.inheritCredentialsFromReferenceRepositoryIn(mappings: List<ChartRepository>) {
        mappings.firstOrNull { it.name == referenceRepository }?.let {
            username = it.username
            password = it.password
        }
    }

    private fun credentialsFor(key: String) = PasswordSafe.instance.get(credentialAttributesFor(key))
    private fun credentialAttributesFor(key: String) = CredentialAttributes(generateServiceName(CREDENTIALS, key))
}
