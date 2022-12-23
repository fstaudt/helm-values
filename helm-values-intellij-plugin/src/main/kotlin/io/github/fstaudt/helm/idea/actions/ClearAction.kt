package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.ClearTask
import java.io.File

class ClearAction : HelmChartAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(ClearTask(event.project!!, File(event.chartFile()!!.path)))
    }
}
