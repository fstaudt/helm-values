package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.service.HelmChartService
import io.github.fstaudt.helm.idea.tasks.actions.AggregateNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateDependencyNotificationAction
import java.io.File

class UpdateDependencyTask(project: Project, private val chartFile: File) :
    BackgroundTask(project, "tasks.updateDependency") {
    private val chartName = chartFile.parentFile.name

    override fun run(indicator: ProgressIndicator) {
        indicator.updateProgress(chartName)
        try {
            HelmChartService.instance.updateRepositories(project)
            HelmChartService.instance.updateDependencies(chartFile)
            success(chartName, AggregateNotificationAction(chartFile))
        } catch (e: Exception) {
            error(chartName, e,
                HelmValuesSettingsNotificationAction(),
                UpdateDependencyNotificationAction(chartFile, "tasks.retry"))
        }
        asyncRefresh()
    }
}
