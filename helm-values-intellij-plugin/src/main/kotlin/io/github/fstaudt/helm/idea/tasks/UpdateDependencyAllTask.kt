package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.service.HelmChartService
import io.github.fstaudt.helm.idea.tasks.actions.AggregateAllNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateDependencyAllNotificationAction

class UpdateDependencyAllTask(private val project: Project) : BackgroundAllTask(project, "tasks.updateDependencyAll") {
    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        try {
            HelmChartService.instance.updateRepositories(project)
        } catch (e: Exception) {
            error("", e,
                HelmValuesSettingsNotificationAction(),
                UpdateDependencyAllNotificationAction("tasks.retry"))
            return
        }
        with(orderedHelmChartDirs()) {
            forEachIndexed { index, dir ->
                indicator.updateProgress(dir.name, index.toDouble() / size)
                try {
                    HelmChartService.instance.updateDependencies(dir.chartFile(), false)
                } catch (e: Exception) {
                    error(dir.name, e,
                        HelmValuesSettingsNotificationAction(),
                        UpdateDependencyAllNotificationAction("tasks.retry"))
                    return
                }
                indicator.checkCanceled()
            }
        }
        success("", AggregateAllNotificationAction())
        asyncRefresh()
    }
}
