package io.github.fstaudt.helm.idea

import com.intellij.openapi.project.Project
import com.intellij.project.stateStore
import java.io.File

internal fun Project.baseDir() = stateStore.projectBasePath.toFile()

internal fun Project.helmChartDirs() = baseDir().helmChartDirs()

internal fun File.chartFile() = File(this, "Chart.yaml")

private fun File.helmChartDirs(): List<File> {
    return listOf(this).filter { it.isHelmChartDir() } + directories().flatMap { dir -> dir.helmChartDirs() }
}

private fun File.directories() = listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
private fun File.isHelmChartDir(): Boolean = chartFile().exists()
