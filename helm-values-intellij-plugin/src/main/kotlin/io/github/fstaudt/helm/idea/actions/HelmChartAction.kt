package io.github.fstaudt.helm.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.vfs.VirtualFile

abstract class HelmChartAction : ProjectAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = (event.project != null && event.chartFile() != null)
    }

    private fun AnActionEvent.virtualFile() = getData(VIRTUAL_FILE)
    protected fun AnActionEvent.chartFile() = virtualFile()?.chartFile()
    private fun VirtualFile.chartFile(): VirtualFile? {
        return when (true) {
            (name == "Chart.yaml") -> this
            isDirectory -> findChild("Chart.yaml")
            (name == "values.yaml") -> findFileByRelativePath("../Chart.yaml")
            else -> null
        }
    }
}
