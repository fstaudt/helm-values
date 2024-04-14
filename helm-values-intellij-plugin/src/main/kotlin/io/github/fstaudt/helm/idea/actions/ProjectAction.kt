package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction

abstract class ProjectAction : DumbAwareAction() {
    protected val progressManager = ProgressManager.getInstance()

    override fun getActionUpdateThread() = BGT
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
