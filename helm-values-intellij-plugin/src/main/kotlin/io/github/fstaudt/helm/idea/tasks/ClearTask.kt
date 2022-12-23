package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.io.File

class ClearTask(private val project: Project, private val chartFile: File) : BackgroundTask(project, "tasks.clear") {
    private val chartName = chartFile.parentFile.name
    override fun run(indicator: ProgressIndicator) {
        indicator.updateProgress(chartName)
        try {
            helmChartService.clear(project, chartFile)
        } catch (e: Exception) {
            error(chartName, e)
        }
        success(chartName)
        asyncRefresh()
    }
}
