package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import io.github.fstaudt.helm.idea.settings.ui.HelmValuesConfigurable

class HelmValuesSettingsNotificationAction : ProjectNotificationAction("tasks.settings") {
    override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        ShowSettingsUtil.getInstance().editConfigurable(event.project!!, HelmValuesConfigurable())
    }
}
