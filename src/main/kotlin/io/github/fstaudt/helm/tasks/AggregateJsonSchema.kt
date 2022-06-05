package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
open class AggregateJsonSchema : JsonSchemaGenerationTask() {
    companion object {
        const val AGGREGATE_JSON_SCHEMA = "aggregateJsonSchema"
        const val AGGREGATED_SCHEMA_FILE = "aggregated-values.schema.json"
    }

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @InputFile
    @PathSensitive(RELATIVE)
    lateinit var chartFile: File

    @InputDirectory
    @PathSensitive(RELATIVE)
    val downloadedSchemasDir = File(project.buildDir, "$HELM_VALUES/$DOWNLOADS")

    @InputDirectory
    @PathSensitive(RELATIVE)
    val unpackSchemasDir = File(project.buildDir, "$HELM_VALUES/$UNPACK")

    @OutputFile
    val aggregatedSchemaFile: File = File(project.buildDir, "$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")

    @TaskAction
    fun aggregate() {
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        val jsonSchema = chart.toAggregatedValuesJsonSchema()
        val globalObjectNode = jsonSchema.objectNode("properties").objectNode("global")
        chart.dependencies.forEach { dependency ->
            extension.repositoryMappings[dependency.repository]?.let { repository ->
                val ref = "$DOWNLOADS/${dependency.aliasOrName()}/${repository.valuesSchemaFile}"
                jsonSchema.objectNode("properties").objectNode(dependency.aliasOrName()).put("\$ref", ref)
                val globalRef = "$DOWNLOADS/${dependency.aliasOrName()}/${repository.globalValuesSchemaFile}"
                globalObjectNode.allOf().add(ObjectNode(nodeFactory).put("\$ref", globalRef))
            }
        }
        jsonMapper.writeValue(aggregatedSchemaFile, jsonSchema)
    }

    private fun Chart.toAggregatedValuesJsonSchema(): ObjectNode {
        return ObjectNode(nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "$name/$version/$AGGREGATED_SCHEMA_FILE")
            .put("title", "Configuration for chart $name/$version")
            .put("description", EMPTY)
    }
}