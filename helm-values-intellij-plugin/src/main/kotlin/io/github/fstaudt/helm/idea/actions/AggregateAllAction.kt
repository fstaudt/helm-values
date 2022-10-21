package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.helmChartDirs
import io.github.fstaudt.helm.idea.service.HelmChartService

class AggregateAllAction : ProjectAction() {
    private val helmChartService = HelmChartService.instance

    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.helmChartDirs().forEach {
            helmChartService.aggregate(event.project!!, it.chartFile())
        }
        asyncRefresh(event)
    }
}
