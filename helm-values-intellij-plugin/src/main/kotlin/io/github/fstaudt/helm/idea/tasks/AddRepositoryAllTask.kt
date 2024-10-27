package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.settings.service.ChartRepositoryService
import io.github.fstaudt.helm.idea.tasks.actions.AddRepositoryNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction

class AddRepositoryAllTask(project: Project) : BackgroundTask(project, "tasks.addRepositoryAll") {

    override fun run(indicator: ProgressIndicator) {
        val state = HelmValuesSettings.instance().state
        indicator.initProgress()
        with(ChartRepositoryService.instance().list()) {
            forEachIndexed { index, repository ->
                indicator.updateProgress(repository.name, index.toDouble() / size)
                try {
                    HelmService.instance().addRepository(repository)
                    state.chartRepositories[repository.name]?.pushedToHelm = true
                } catch (e: Exception) {
                    state.chartRepositories[repository.name]?.pushedToHelm = false
                    error(repository.name, e,
                        HelmValuesSettingsNotificationAction("tasks.helmRepo"),
                        AddRepositoryNotificationAction(repository)
                    )
                }
            }
        }
        if (state.chartRepositories.all { it.value.pushedToHelm }) {
            success("", HelmValuesSettingsNotificationAction("tasks.helmRepo"))
        }
    }
}
