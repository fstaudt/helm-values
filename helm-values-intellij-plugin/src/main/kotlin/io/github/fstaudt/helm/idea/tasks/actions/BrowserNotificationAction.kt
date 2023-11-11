package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.ide.BrowserUtil.browse
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent

open class BrowserNotificationAction(key: String, private val url: String) : ProjectNotificationAction(key) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) = browse(url)
}
