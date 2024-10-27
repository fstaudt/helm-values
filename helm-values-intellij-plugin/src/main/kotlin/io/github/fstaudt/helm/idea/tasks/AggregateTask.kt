package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService
import io.github.fstaudt.helm.idea.tasks.actions.AggregateNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateDependencyNotificationAction
import java.io.File

class AggregateTask(private val project: Project, private val chartFile: File) :
    BackgroundTask(project, "tasks.aggregate") {
    private val chartName = chartFile.parentFile.name

    override fun run(indicator: ProgressIndicator) {
        indicator.updateProgress(chartName)
        try {
            val complete = HelmJsonSchemaService.instance().aggregate(project, chartFile)
            if (complete) {
                success(chartName)
            } else {
                warning(chartName, UpdateDependencyNotificationAction(chartFile))
            }
        } catch (e: Exception) {
            error(chartName, e, AggregateNotificationAction(chartFile, "tasks.retry"))
        }
        asyncRefresh()
    }
}
