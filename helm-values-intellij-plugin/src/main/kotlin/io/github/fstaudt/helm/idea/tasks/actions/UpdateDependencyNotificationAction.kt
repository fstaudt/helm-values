package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateDependencyTask
import java.io.File

class UpdateDependencyNotificationAction(private val chartFile: File, key: String = "tasks.updateDependency") :
    ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(UpdateDependencyTask(event.project!!, chartFile))
        notification.expire()
    }
}
