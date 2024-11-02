package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateRepositoryAllTask

class UpdateRepositoryAllAction : ProjectAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(UpdateRepositoryAllTask(event.project!!))
    }
}
