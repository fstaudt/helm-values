package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateHelmMetadataTask

class UpdateHelmMetadataNotificationAction(key: String = "tasks.updateHelmMetadata") :
    ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(UpdateHelmMetadataTask(event.project!!))
        notification.expire()
    }
}
