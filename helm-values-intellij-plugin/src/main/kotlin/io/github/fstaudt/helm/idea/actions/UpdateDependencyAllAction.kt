package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateDependencyAllTask

class UpdateDependencyAllAction : ProjectAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(UpdateDependencyAllTask(event.project!!))
    }
}
