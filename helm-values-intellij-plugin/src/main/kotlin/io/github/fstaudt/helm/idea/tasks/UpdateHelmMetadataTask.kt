package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.tasks.actions.UpdateRepositoryAllNotificationAction
import java.time.OffsetDateTime.now
import java.time.temporal.ChronoUnit.DAYS

class UpdateHelmMetadataTask(private val project: Project) : BackgroundAllTask(project, "tasks.updateHelmMetadata") {
    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        try {
            HelmService.instance().updateHelmMetadata(project, now().truncatedTo(DAYS))
        } catch (e: Exception) {
            error("", e,
                UpdateRepositoryAllNotificationAction("tasks.retry"))
            return
        }
        success("")
    }
}
