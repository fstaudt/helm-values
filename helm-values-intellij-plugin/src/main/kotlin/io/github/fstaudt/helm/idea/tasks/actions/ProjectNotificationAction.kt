package io.github.fstaudt.helm.idea.tasks.actions

import com.intellij.notification.NotificationAction
import com.intellij.openapi.progress.ProgressManager
import io.github.fstaudt.helm.idea.HelmValuesBundle

abstract class ProjectNotificationAction(key: String) :
    NotificationAction(HelmValuesBundle.message("$key.title")) {
    protected val progressManager = ProgressManager.getInstance()
}
