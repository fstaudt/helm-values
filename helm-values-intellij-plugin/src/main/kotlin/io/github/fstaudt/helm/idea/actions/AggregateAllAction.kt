package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import io.github.fstaudt.helm.idea.helmChartDirs
import io.github.fstaudt.helm.idea.service.AggregationService
import java.io.File

class AggregateAllAction : DumbAwareAction() {
    private val aggregationService = AggregationService.instance

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.helmChartDirs().forEach {
            aggregationService.aggregate(event.project!!, it.chartFile())
        }
        VirtualFileManager.getInstance().asyncRefresh {
            event.project!!.getService(JsonSchemaService::class.java).reset()
        }
    }

    private fun File.chartFile() = listFiles().first { it.name == "Chart.yaml" }
}
