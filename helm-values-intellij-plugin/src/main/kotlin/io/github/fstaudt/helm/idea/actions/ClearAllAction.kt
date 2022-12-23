package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.ClearAllTask

class ClearAllAction : ProjectAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(ClearAllTask(event.project!!))
    }
}
