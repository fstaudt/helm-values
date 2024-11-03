package io.github.fstaudt.helm.idea

import com.intellij.openapi.project.Project
import com.intellij.project.stateStore
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.CHART_METADATA_FILE
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.JSON_SCHEMAS_DIR
import java.io.File

internal fun Project.baseDir() = stateStore.projectBasePath.toFile()

internal fun Project.helmChartDirs() = baseDir().helmChartDirs().sortedBy { it.canonicalPath }

internal fun File.chartFile() = File(this, HELM_CHART_FILE)
internal fun Project.jsonSchemasDirs(): List<File> {
    return File(baseDir(), JSON_SCHEMAS_DIR).directories().filter { it.isJsonSchemasDir() }
}

private fun File.helmChartDirs(): List<File> {
    return listOf(this).filter { it.isHelmChartDir() } + directories().flatMap { dir -> dir.helmChartDirs() }
}

private fun File.directories(): List<File> {
    return listFiles { file ->
        file.isDirectory && file.name != ".idea" && file.name != "helm-values"
    }?.toList() ?: emptyList()
}

private fun File.isHelmChartDir(): Boolean = chartFile().exists()
private fun File.isJsonSchemasDir(): Boolean = File(this, CHART_METADATA_FILE).exists()
