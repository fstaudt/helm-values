package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.tasks.actions.HelmInstallBrowserNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.HelmValuesSettingsNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateDependencyAllNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateRepositoryAllNotificationAction

class UpdateRepositoryAllTask(private val project: Project) : BackgroundAllTask(project, "tasks.updateRepositoryAll") {
    override fun run(indicator: ProgressIndicator) {
        indicator.initProgress()
        try {
            HelmService.instance().updateRepositories(project)
        } catch (e: Exception) {
            error("", e,
                HelmInstallBrowserNotificationAction(),
                HelmValuesSettingsNotificationAction(),
                UpdateRepositoryAllNotificationAction("tasks.retry"))
            return
        }
        success("", UpdateDependencyAllNotificationAction())
    }
}
