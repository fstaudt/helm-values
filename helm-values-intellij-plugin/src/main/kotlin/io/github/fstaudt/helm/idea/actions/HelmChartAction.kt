package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.vfs.VirtualFile

abstract class HelmChartAction : ProjectAction() {
    override fun update(event: AnActionEvent) {
        event.getData(VIRTUAL_FILE)?.let {
            event.presentation.isEnabledAndVisible = event.chartFile() != null
        }
    }

    protected fun AnActionEvent.chartFile(): VirtualFile? {
        return getData(VIRTUAL_FILE)?.run {
            when (true) {
                isDirectory -> findChild("Chart.yaml")
                else -> findFileByRelativePath("../Chart.yaml")
            }
        }
    }
}
