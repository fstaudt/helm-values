package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.fstaudt.helm.idea.tasks.UpdateDependencyTask
import java.io.File

class UpdateDependencyAction : HelmChartAction() {
    override fun actionPerformed(event: AnActionEvent) {
        progressManager.run(UpdateDependencyTask(event.project!!, File(event.chartFile()!!.path)))
    }
}
