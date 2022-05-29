package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmValuesAssistantExtension.Companion.EXTENSION
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.GenerateJsonSchema
import io.github.fstaudt.helm.tasks.GenerateJsonSchema.Companion.GENERATE_JSON_SCHEMA
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
        const val VALUES_SCHEMA_FILE = "helm-values.json"
        const val GLOBAL_VALUES_SCHEMA_FILE = "helm-global.json"
    }

    override fun apply(project: Project) {
        with(project) {
            val pluginExtension = extensions.create(EXTENSION, HelmValuesAssistantExtension::class.java)
            val helmChartFile = File(projectDir, "${pluginExtension.sourcesDirectory}/Chart.yaml")
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
                chartsFolder = File("${projectDir}/${pluginExtension.sourcesDirectory}/charts")
            }
            tasks.register<GenerateJsonSchema>(GENERATE_JSON_SCHEMA) {
                group = HELM_VALUES
                description = "Generate JSON schema for publication to an external schema repository"
                extension = pluginExtension
                chartFile = helmChartFile
            }
        }
    }
}