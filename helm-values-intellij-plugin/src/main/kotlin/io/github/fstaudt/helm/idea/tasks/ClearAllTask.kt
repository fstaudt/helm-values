package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.helmChartDirs
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService
import io.github.fstaudt.helm.idea.tasks.actions.AggregateAllNotificationAction

class ClearAllTask(private val project: Project) : BackgroundTask(project, "tasks.clearAll") {
    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        with(project.helmChartDirs()) {
            forEachIndexed { index, dir ->
                indicator.updateProgress(dir.name, index.toDouble() / size)
                try {
                    HelmJsonSchemaService.instance().clear(project, dir.chartFile())
                } catch (e: Exception) {
                    error(dir.name, e)
                    return
                }
                indicator.checkCanceled()
            }
        }
        success("", AggregateAllNotificationAction())
        asyncRefresh()
    }
}
