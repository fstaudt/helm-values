package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.AggregateAllTask

class AggregateAllNotificationAction(key: String = "tasks.aggregateAll") : ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(AggregateAllTask(event.project!!))
        notification.expire()
    }
}
