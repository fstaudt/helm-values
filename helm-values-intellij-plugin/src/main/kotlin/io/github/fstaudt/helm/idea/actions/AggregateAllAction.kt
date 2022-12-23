package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.AggregateAllTask

class AggregateAllAction : ProjectAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(AggregateAllTask(event.project!!))
    }
}
