package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.credentialStore.CredentialStoreUiService
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent

class PasswordSafeNotificationAction(key: String = "settings.password") : ProjectNotificationAction(key) {
  override fun actionPerformed(event: AnActionEvent, notification: Notification) {
    CredentialStoreUiService.getInstance().openSettings(event.project!!)
    notification.expire()
  }
}
