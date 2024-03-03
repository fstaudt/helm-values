package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.settings.service.ChartRepositoryService
import io.github.fstaudt.helm.idea.tasks.actions.AddRepositoryNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction

class AddRepositoryAllTask(project: Project) : BackgroundTask(project, "tasks.addRepositoryAll") {

    private val chartRepositoryService = ChartRepositoryService.instance
    private val helmService = HelmService.instance
    private val state = HelmValuesSettings.instance.state

    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        with(chartRepositoryService.list()) {
            forEachIndexed { index, repository ->
                indicator.updateProgress(repository.name, index.toDouble() / size)
                try {
                    helmService.addRepository(repository)
                    state.chartRepositories[repository.name]?.synchronized = true
                } catch (e: Exception) {
                    state.chartRepositories[repository.name]?.synchronized = false
                    error(repository.name, e,
                        HelmValuesSettingsNotificationAction("tasks.helmRepo"),
                        AddRepositoryNotificationAction(repository)
                    )
                }
            }
        }
        if (state.chartRepositories.all { it.value.synchronized }) {
            success("", HelmValuesSettingsNotificationAction("tasks.helmRepo"))
        }
    }
}
