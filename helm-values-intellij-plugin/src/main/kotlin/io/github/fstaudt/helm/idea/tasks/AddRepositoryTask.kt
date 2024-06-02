package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.tasks.actions.AddRepositoryNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction

class AddRepositoryTask(project: Project?, private val repository: ChartRepository) :
    NotifiableTask(project, "tasks.addRepository") {

    private val helmService = HelmService.instance
    private val state = HelmValuesSettings.instance.state

    override fun run(indicator: ProgressIndicator) {
        runSynchronously()
    }

    fun runSynchronously() {
        try {
            helmService.addRepository(repository)
            state.chartRepositories[repository.name]?.pushedToHelm = true
            success(repository.name)
        } catch (e: Exception) {
            state.chartRepositories[repository.name]?.pushedToHelm = false
            error(repository.name, e,
                HelmValuesSettingsNotificationAction("tasks.helmRepo"),
                AddRepositoryNotificationAction(repository)
            )
        }
    }
}
