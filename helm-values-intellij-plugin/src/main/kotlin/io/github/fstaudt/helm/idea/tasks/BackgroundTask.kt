package io.github.fstaudt.helm.idea.tasks

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.ConditionalModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import io.github.fstaudt.helm.idea.HelmValuesBundle.message

abstract class BackgroundTask(private val project: Project, private val key: String) :
    ConditionalModal(project, message("$key.title"), false, ALWAYS_BACKGROUND) {

    protected fun asyncRefresh() {
        VirtualFileManager.getInstance().asyncRefresh {
            project.getService(JsonSchemaService::class.java).reset()
        }
    }

    protected fun ProgressIndicator.initProgress() {
        text = message("$key.init")
    }

    protected fun ProgressIndicator.updateProgress(chartName: String, progress: Double? = null) {
        text = message("$key.indicator", chartName)
        progress?.also {
            isIndeterminate = false
            fraction = it
        }
    }

    protected fun success(param: String = "", vararg actions: NotificationAction) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("helm.values.notifications")
            .createNotification(message("$key.info", param), INFORMATION)
            .apply { addActions(actions.toSet()) }
            .notify(project)
    }

    protected fun warning(param: String = "", vararg actions: NotificationAction) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("helm.values.errors")
            .createNotification(message("$key.warning", param), WARNING)
            .apply { addActions(actions.toSet()) }
            .notify(project)
    }

    protected fun error(param: String, exception: Exception, vararg actions: NotificationAction) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("helm.values.errors")
            .createNotification(message("$key.error", param), exception.localizedMessage, ERROR)
            .apply { addActions(actions.toSet()) }
            .notify(project)
    }
}
