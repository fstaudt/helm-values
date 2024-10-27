package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.credentialStore.PasswordSafeConfigurable
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class PasswordSafeNotificationAction(key: String = "settings.password") : ProjectNotificationAction(key) {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        ShowSettingsUtil.getInstance().editConfigurable(event.project!!, PasswordSafeConfigurable())
        notification.expire()
    }
}
