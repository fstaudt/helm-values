package io.github.fstaudt.helm.idea.service

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.Mappers.chartMapper
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.exceptions.ProcessFailureException
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.model.Chart
import java.io.File

@Service
class HelmService {
    companion object {
        fun instance(): HelmService =
            ApplicationManager.getApplication().getService(HelmService::class.java)
    }

    fun addRepository(repository: ChartRepository) {
        val state = HelmValuesSettings.instance().state
        with(repository) {
            val parameters = if (secured()) {
                arrayOf("repo", "add", name, url,
                    "--username", username,
                    "--password", password,
                    "--force-update")
            } else {
                arrayOf("repo", "add", name, url, "--force-update")
            }
            GeneralCommandLine()
                .withExePath(state.helmBinaryPath)
                .withParameters(*parameters)
                .execute {
                    if (timeout)
                        throw ProcessFailureException("Timeout while adding Helm repository $name")
                    if (exitCode != 0)
                        throw ProcessFailureException(output)
                }
        }
    }

    fun removeRepository(repository: ChartRepository) {
        val state = HelmValuesSettings.instance().state
        with(repository) {
            GeneralCommandLine()
                .withExePath(state.helmBinaryPath)
                .withParameters("repo", "remove", name)
                .execute {
                    if (timeout)
                        throw ProcessFailureException("Timeout while deleting Helm repository $name")
                    if (exitCode != 0)
                        throw ProcessFailureException(output)
                }
        }
    }

    fun updateRepositories(project: Project) {
        val state = HelmValuesSettings.instance().state
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
        val state = HelmValuesSettings.instance().state
        if (updateLocalDependencies) {
            val chart = chartFile.inputStream().use { chartMapper.readValue(it, Chart::class.java) }
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
