package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateDependencyAllTask

class UpdateDependencyAllNotificationAction(key: String = "tasks.updateDependencyAll") :
    ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(UpdateDependencyAllTask(event.project!!))
        notification.expire()
    }
}
