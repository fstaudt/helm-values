package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService

abstract class ProjectAction : DumbAwareAction() {

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    protected fun asyncRefresh(event: AnActionEvent) {
        VirtualFileManager.getInstance().asyncRefresh {
            event.project!!.getService(JsonSchemaService::class.java).reset()
        }
    }
}
