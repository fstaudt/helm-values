package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmValuesAssistantExtension.Companion.EXTENSION
import io.github.fstaudt.helm.tasks.HelmDownloadJsonSchemas
import io.github.fstaudt.helm.tasks.HelmDownloadJsonSchemas.Companion.HELM_DOWNLOAD_JSON_SCHEMAS
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.io.File

class HelmValuesAssistantPlugin : Plugin<Project> {
    companion object {
        const val HELM_VALUES = "helm-values-assistant"
    }
    override fun apply(project: Project) {
        with(project) {
            val pluginExtension = extensions.create(EXTENSION, HelmValuesAssistantExtension::class.java)
            tasks.register<HelmDownloadJsonSchemas>(HELM_DOWNLOAD_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Download JSON schemas of dependencies from external schema repositories"
                extension = pluginExtension
                chartFile = File(projectDir, "${extension.sourcesDirectory}/Chart.yaml")
            }
        }
    }
}