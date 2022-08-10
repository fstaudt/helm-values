package io.github.fstaudt.helm.gradle

import io.github.fstaudt.helm.gradle.HelmValuesExtension.Companion.EXTENSION
import io.github.fstaudt.helm.gradle.tasks.AggregateJsonSchema
import io.github.fstaudt.helm.gradle.tasks.AggregateJsonSchema.Companion.AGGREGATE_JSON_SCHEMA
import io.github.fstaudt.helm.gradle.tasks.DownloadJsonSchemas
import io.github.fstaudt.helm.gradle.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.tasks.ExtractJsonSchemas
import io.github.fstaudt.helm.gradle.tasks.ExtractJsonSchemas.Companion.CHARTS_DIR
import io.github.fstaudt.helm.gradle.tasks.ExtractJsonSchemas.Companion.EXTRACT_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.tasks.GenerateJsonSchemas
import io.github.fstaudt.helm.gradle.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.tasks.PublishJsonSchemas
import io.github.fstaudt.helm.gradle.tasks.PublishJsonSchemas.Companion.PUBLISH_JSON_SCHEMAS
import io.github.fstaudt.helm.http.NexusRawJsonSchemaPublisher
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.io.File

class HelmValuesPlugin : Plugin<Project> {
    companion object {
        const val HELM_VALUES = "helm-values"
        const val SCHEMA_VERSION = "https://json-schema.org/draft/2020-12/schema"
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
            val extractJsonSchemas = tasks.register<ExtractJsonSchemas>(EXTRACT_JSON_SCHEMAS) {
                group = HELM_VALUES
                description = "Extract JSON schemas values.schema.json from chart dependencies"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
                chartsDir = File("${projectDir}/${pluginExtension.sourcesDir}/$CHARTS_DIR").takeIf { it.exists() }
            }
            tasks.register<AggregateJsonSchema>(AGGREGATE_JSON_SCHEMA) {
                group = HELM_VALUES
                description = "Aggregate extracted and downloaded JSON schemas for assistance on Helm values in your IDE"
                extension = pluginExtension
                chartFile = File(projectDir, "${pluginExtension.sourcesDir}/Chart.yaml").takeIf { it.exists() }
                dependsOn(downloadJsonSchemas, extractJsonSchemas)
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