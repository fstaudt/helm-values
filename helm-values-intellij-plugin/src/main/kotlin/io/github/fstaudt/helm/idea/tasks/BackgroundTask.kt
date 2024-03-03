package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import io.github.fstaudt.helm.idea.HelmValuesBundle.message

abstract class BackgroundTask(private val project: Project, private val key: String) : NotifiableTask(project, key) {

    protected fun asyncRefresh() {
        VirtualFileManager.getInstance().asyncRefresh {
            project.getService(JsonSchemaService::class.java).reset()
        }
    }

    protected fun ProgressIndicator.initProgress() {
        text = message("$key.init")
    }

    protected fun ProgressIndicator.updateProgress(chartName: String, progress: Double? = null) {
        text = message("$key.indicator", chartName)
        progress?.also {
            isIndeterminate = false
            fraction = it
        }
    }
}
