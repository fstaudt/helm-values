package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateRepositoryAllTask

class UpdateRepositoryAllNotificationAction(key: String = "tasks.updateRepositoryAll") :
    ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(UpdateRepositoryAllTask(event.project!!))
        notification.expire()
    }
}
