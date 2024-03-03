package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.AddRepositoryAllTask

class AddRepositoryAllAction : ProjectAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(AddRepositoryAllTask(event.project!!))
    }
}
