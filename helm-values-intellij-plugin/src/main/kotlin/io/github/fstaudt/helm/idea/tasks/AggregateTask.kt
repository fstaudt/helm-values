package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.io.File

class AggregateTask(private val project: Project, private val chartFile: File) :
    BackgroundTask(project, "tasks.aggregate") {
    private val chartName = chartFile.parentFile.name
    override fun run(indicator: ProgressIndicator) {
        indicator.updateProgress(chartName)
        try {
            helmChartService.aggregate(project, chartFile)
            success(chartName)
        } catch (e: Exception) {
            error(chartName, e)
        }
        asyncRefresh()
    }
}
