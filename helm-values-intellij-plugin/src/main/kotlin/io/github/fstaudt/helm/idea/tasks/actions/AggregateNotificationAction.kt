package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.AggregateTask
import java.io.File

class AggregateNotificationAction(private val chartFile: File, key: String = "tasks.aggregate") :
    ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(AggregateTask(event.project!!, chartFile))
        notification.expire()
    }
}
