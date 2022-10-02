package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import io.github.fstaudt.helm.idea.service.AggregationService
import java.io.File

class AggregateAction : DumbAwareAction() {
    private val aggregationService = AggregationService.instance

    override fun update(event: AnActionEvent) {
        event.getData(VIRTUAL_FILE)?.let {
            event.presentation.isEnabledAndVisible =
                (it.isDirectory || it.name in arrayOf("Chart.yaml", "values.yaml")) && event.chartFile() != null
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        aggregationService.aggregate(event.project!!, File(event.chartFile()!!.path))
        VirtualFileManager.getInstance().asyncRefresh {
            event.project!!.getService(JsonSchemaService::class.java).reset()
        }
    }

    private fun AnActionEvent.chartFile(): VirtualFile? {
        return getData(VIRTUAL_FILE)?.run {
            when (true) {
                isDirectory -> findChild("Chart.yaml")
                else -> findFileByRelativePath("../Chart.yaml")
            }
        }
    }
}
