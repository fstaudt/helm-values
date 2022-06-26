package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmValuesExtension.Companion.EXTENSION
import io.github.fstaudt.helm.http.NexusRawJsonSchemaPublisher
import io.github.fstaudt.helm.tasks.AggregateJsonSchema
import io.github.fstaudt.helm.tasks.AggregateJsonSchema.Companion.AGGREGATE_JSON_SCHEMA
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.PublishJsonSchemas
import io.github.fstaudt.helm.tasks.PublishJsonSchemas.Companion.PUBLISH_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.CHARTS_DIR
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK_JSON_SCHEMAS
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.io.File

class HelmValuesPlugin : Plugin<Project> {
    companion object {
        const val HELM_VALUES = "helm-values"
        const val SCHEMA_VERSION = "https://json-schema.org/draft/2020-12/schema"
        const val VALUES_SCHEMA_FILE = "values.schema.json"
        const val GLOBAL_VALUES_SCHEMA_FILE = "global-values.schema.json"
    }

    override fun apply(project: Project) {
        with(project) {
            val pluginExtension = extensions.create(EXTENSION, HelmValuesExtension::class.java)
            val downloadJsonSchemas = tasks.register<DownloadJsonSchemas>(DOWNLOAD_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Download JSON schemas of dependencies from JSON schema repositories"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
            }
            val unpackJsonSchemas = tasks.register<UnpackJsonSchemas>(UNPACK_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Unpack JSON schemas values.schema.json from chart dependencies"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
                chartsDir = File("${projectDir}/${pluginExtension.sourcesDir}/$CHARTS_DIR").takeIf { it.exists() }
            }
            tasks.register<AggregateJsonSchema>(AGGREGATE_JSON_SCHEMA) {
                group = HELM_VALUES
                description = "Aggregate unpacked and downloaded JSON schemas for assistance on Helm values in your IDE"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
                dependsOn(downloadJsonSchemas, unpackJsonSchemas)
            }
            val generateJsonSchemas = tasks.register<GenerateJsonSchemas>(GENERATE_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Generate JSON schemas for publication to a repository of JSON schemas"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
            }
            tasks.register<PublishJsonSchemas>(PUBLISH_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Publish generated JSON schemas to a repository of JSON schemas"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
                jsonSchemaPublisher = NexusRawJsonSchemaPublisher()
                dependsOn(generateJsonSchemas)
            }
        }
    }
}