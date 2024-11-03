package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.tasks.actions.AggregateAllNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateDependencyAllNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateRepositoryAllNotificationAction

class UpdateDependencyAllTask(project: Project) : BackgroundAllTask(project, "tasks.updateDependencyAll") {
    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        with(orderedHelmChartDirs()) {
            forEachIndexed { index, dir ->
                indicator.updateProgress(dir.name, index.toDouble() / size)
                try {
                    HelmService.instance().updateDependencies(dir.chartFile(), false)
                } catch (e: Exception) {
                    error(dir.name, e,
                        HelmValuesSettingsNotificationAction("tasks.helmRepo"),
                        UpdateRepositoryAllNotificationAction(),
                        UpdateDependencyAllNotificationAction("tasks.retry"))
                    return
                }
                indicator.checkCanceled()
            }
        }
        success("", AggregateAllNotificationAction())
    }
}
