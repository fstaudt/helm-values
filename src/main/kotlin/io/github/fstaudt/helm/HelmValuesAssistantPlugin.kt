package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmValuesAssistantExtension.Companion.EXTENSION
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK_JSON_SCHEMAS
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.io.File

class HelmValuesAssistantPlugin : Plugin<Project> {
    companion object {
        const val HELM_VALUES = "helm-values-assistant"
        const val SCHEMA_VERSION = "https://json-schema.org/draft/2020-12/schema"
    }

    override fun apply(project: Project) {
        with(project) {
            val pluginExtension = extensions.create(EXTENSION, HelmValuesAssistantExtension::class.java)
            val helmChartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml")
            tasks.register<DownloadJsonSchemas>(DOWNLOAD_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Download JSON schemas of dependencies from external schema repositories"
                extension = pluginExtension
                chartFile = helmChartFile
            }
            tasks.register<UnpackJsonSchemas>(UNPACK_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Unpack JSON schemas values.schema.json from chart dependencies"
                extension = pluginExtension
                chartFile = helmChartFile
                chartsDir = File("${projectDir}/${pluginExtension.sourcesDir}/charts")
            }
            tasks.register<GenerateJsonSchemas>(GENERATE_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Generate JSON schemas for publication to a repository of JSON schemas"
                extension = pluginExtension
                chartFile = helmChartFile
            }
        }
    }
}