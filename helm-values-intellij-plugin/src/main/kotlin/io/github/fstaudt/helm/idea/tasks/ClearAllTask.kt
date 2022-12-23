package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.helmChartDirs

class ClearAllTask(private val project: Project) : BackgroundTask(project, "tasks.clearAll") {
    override fun run(indicator: ProgressIndicator) {
        with(project.helmChartDirs()) {
            forEachIndexed { index, dir ->
                indicator.updateProgress(dir.name, index.toDouble() / size)
                try {
                    helmChartService.clear(project, dir.chartFile())
                } catch (e: Exception) {
                    error(dir.name, e)
                }
                indicator.checkCanceled()
            }
        }
        success()
        asyncRefresh()
    }
}
