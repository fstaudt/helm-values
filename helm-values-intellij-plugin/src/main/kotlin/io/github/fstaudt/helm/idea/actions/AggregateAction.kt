package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.service.HelmChartService
import java.io.File

class AggregateAction : HelmChartAction() {
    private val helmChartService = HelmChartService.instance

    override fun actionPerformed(event: AnActionEvent) {
        helmChartService.aggregate(event.project!!, File(event.chartFile()!!.path))
        asyncRefresh(event)
    }
}
