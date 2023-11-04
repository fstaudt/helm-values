package io.github.fstaudt.helm.idea.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.exceptions.ProcessFailureException
import io.github.fstaudt.helm.model.Chart
import java.io.File

@Service
class HelmChartService {
    companion object {
        val instance: HelmChartService =
            ApplicationManager.getApplication().getService(HelmChartService::class.java)
    }

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val state = HelmValuesSettings.instance.state

    fun updateRepositories(project: Project) {
        GeneralCommandLine()
            .withWorkDirectory(project.baseDir())
            .withExePath(state.helmBinaryPath)
            .withParameters("repo", "update")
            .execute {
                if (timeout)
                    throw ProcessFailureException("Timeout while updating repositories")
                if (exitCode != 0)
                    throw ProcessFailureException(output)
            }
    }

    fun updateDependencies(chartFile: File, updateLocalDependencies: Boolean = true) {
        if (updateLocalDependencies) {
            val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
            chart.dependencies.filter { it.isStoredLocally() }.forEach { dependency ->
                File(chartFile.parentFile, "${dependency.localPath()}/$HELM_CHART_FILE").canonicalFile
                    .takeIf { it.exists() }?.let { updateDependencies(it) }
            }
        }
        GeneralCommandLine()
            .withWorkDirectory(chartFile.parentFile)
            .withExePath(state.helmBinaryPath)
            .withParameters("dependency", "update", ".", "--skip-refresh")
            .execute {
                if (timeout)
                    throw ProcessFailureException("Timeout while updating dependencies for chart ${chartFile.parent}")
                if (exitCode != 0)
                    throw ProcessFailureException(output)
            }
    }
}
