package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.service.HelmService
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.tasks.actions.RemoveRepositoryNotificationAction

class RemoveRepositoryTask(project: Project?, private val repository: ChartRepository) :
    NotifiableTask(project, "tasks.removeRepository") {

    override fun run(indicator: ProgressIndicator) {
        try {
            HelmService.instance().removeRepository(repository)
            success(repository.name)
        } catch (e: Exception) {
            error(repository.name, e, RemoveRepositoryNotificationAction(repository))
        }
    }
}
