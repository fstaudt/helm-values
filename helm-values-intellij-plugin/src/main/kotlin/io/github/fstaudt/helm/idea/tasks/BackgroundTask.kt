package io.github.fstaudt.helm.idea.tasks

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.ConditionalModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.rd.util.string.printToString
import io.github.fstaudt.helm.idea.HelmValuesBundle.message

abstract class BackgroundTask(private val project: Project, private val key: String) :
    ConditionalModal(project, message("$key.title"), false, ALWAYS_BACKGROUND) {

    protected fun asyncRefresh() {
        VirtualFileManager.getInstance().asyncRefresh {
            project.getService(JsonSchemaService::class.java).reset()
        }
    }

    protected fun ProgressIndicator.updateProgress(chartName: String, progress: Double? = null) {
        text = message("$key.indicator", chartName)
        progress?.also {
            isIndeterminate = false
            fraction = it
        }
    }

    protected fun success(chartName: String = "") {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("helm.values.notifications")
            .createNotification(message("$key.info", chartName), INFORMATION)
            .notify(project)
    }

    protected fun error(chartName: String, exception: Exception) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("helm.values.errors")
            .createNotification(message("$key.error", chartName), exception.printToString(), ERROR)
            .notify(project)
    }
}
