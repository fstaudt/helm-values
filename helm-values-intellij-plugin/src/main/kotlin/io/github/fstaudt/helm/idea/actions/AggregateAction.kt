package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.AggregateTask
import java.io.File

class AggregateAction : HelmChartAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(AggregateTask(event.project!!, File(event.chartFile()!!.path)))
    }
}
