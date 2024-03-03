package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.tasks.AddRepositoryTask

class AddRepositoryNotificationAction(private val repository: ChartRepository) :
    ProjectNotificationAction("tasks.retry") {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        progressManager.run(AddRepositoryTask(event.project!!, repository))
        notification.expire()
    }
}
