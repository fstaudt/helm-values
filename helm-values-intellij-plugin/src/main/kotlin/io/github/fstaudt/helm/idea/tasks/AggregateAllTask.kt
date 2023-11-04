package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService
import io.github.fstaudt.helm.idea.tasks.actions.AggregateAllNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateDependencyNotificationAction

class AggregateAllTask(private val project: Project) : BackgroundAllTask(project, "tasks.aggregateAll") {
    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        with(orderedHelmChartDirs()) {
            forEachIndexed { index, dir ->
                indicator.updateProgress(dir.name, index.toDouble() / size)
                try {
                    val complete = HelmJsonSchemaService.instance.aggregate(project, dir.chartFile(), false)
                    if (!complete) {
                        warning(dir.name, UpdateDependencyNotificationAction(dir.chartFile()))
                    }
                } catch (e: Exception) {
                    error(dir.name, e, AggregateAllNotificationAction("tasks.retry"))
                    return
                }
                indicator.checkCanceled()
            }
        }
        success()
        asyncRefresh()
    }
}
