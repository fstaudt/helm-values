package io.github.fstaudt.helm.aggregation.schema

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.NEW_LINE
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.global
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.globalOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.props
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.propsOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.removeAdditionalAndUnevaluatedProperties
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.toObjectNode
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.DEFS
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.model.RefMapping
import io.github.fstaudt.helm.model.RefMapping.Companion.updateReferencesFor
import java.io.File

/**
 * Aggregator for JSON schemas of dependencies extracted from dependency archives
 */
class ExtractedSchemaAggregator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val extractsDir: File
) {
    companion object {
        const val EXTRACTED_GLOBAL_VALUES_TITLE = "Aggregated global values for"
        const val EXTRACTS = "extracts"
        private val yamlMapper = ObjectMapper(YAMLFactory()).also {
            it.registerModule(KotlinModule.Builder().build())
            it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    fun aggregateFor(chart: Chart, jsonSchema: ObjectNode) {
        chart.dependencies.filter { it.shouldBeAggregated() }.forEach { dependency ->
            jsonSchema.aggregateFor(dependency, extractsDir, "#/$DEFS/$EXTRACTS", jsonSchema)
        }
    }

    private fun ObjectNode.aggregateFor(
        dependency: ChartDependency,
        extractedChartDir: File,
        refPrefix: String,
        jsonSchema: ObjectNode
    ) {
        val dependencyRefPrefix = "$refPrefix/${dependency.aliasOrName()}"
        val dependencyDir = File(extractedChartDir, dependency.name)
        val dependencyChart = File(dependencyDir, HELM_CHART_FILE).takeIf { it.exists() }?.let { chartFile ->
            chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        }
        with(props().objectNode(dependency.aliasOrName())) {
            File(dependencyDir, HELM_SCHEMA_FILE).takeIf { it.exists() }?.let {
                put("\$ref", "$dependencyRefPrefix/$HELM_SCHEMA_FILE")
                jsonSchema.aggregateExtractedSchemaFor(dependencyDir,
                    "$dependencyRefPrefix/$HELM_SCHEMA_FILE".removePrefix("#/"))
            }
            dependencyChart?.dependencies?.forEach {
                aggregateFor(it, dependencyDir, dependencyRefPrefix, jsonSchema)
            }
            propsOrNull()?.globalOrNull()?.let {
                addGlobalPropertiesDescriptionFor(dependencyRefPrefix.removePrefix("#/$DEFS/${EXTRACTS}/"))
            }
            if (has("\$ref") && size() > 1) {
                allOf().add(objectNode().set("\$ref", remove("\$ref")) as JsonNode)
            }
        }
        addGlobalPropertiesFor(dependency, extractedChartDir, refPrefix)
    }

    private fun ChartDependency.shouldBeAggregated(): Boolean {
        return version != null && !isStoredLocally() && !repositoryMappings.contains(repository)
    }

    private fun ObjectNode.addGlobalPropertiesDescriptionFor(dependencyName: String) {
        props().global().allOf().add(
            objectNode()
                .put("title", "$EXTRACTED_GLOBAL_VALUES_TITLE $dependencyName dependency")
                .put("description", NEW_LINE)
        )
    }

    private fun ObjectNode.addGlobalPropertiesFor(
        dependency: ChartDependency,
        extractedChartDir: File,
        refPrefix: String
    ) {
        val dependencyRefPrefix = "$refPrefix/${dependency.aliasOrName()}"
        val dependencyDir = File(extractedChartDir, dependency.name)
        File(dependencyDir, HELM_SCHEMA_FILE).takeIf { it.exists() }?.let {
            val ref = "$dependencyRefPrefix/$HELM_SCHEMA_FILE/properties/global"
            props().global().allOf().add(objectNode().put("\$ref", ref))
        }
        val dependencyChart = File(dependencyDir, HELM_CHART_FILE).takeIf { it.exists() }?.let { chartFile ->
            chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        }
        dependencyChart?.dependencies?.forEach {
            addGlobalPropertiesFor(it, dependencyDir, dependencyRefPrefix)
        }
    }

    private fun ObjectNode.aggregateExtractedSchemaFor(schemaDir: File, schemaPath: String) {
        val schemaNode = schemaPath.split("/").filter { it.isNotBlank() }
            .fold(this) { node, s -> node.objectNode(s) }
        val schema = File(schemaDir, HELM_SCHEMA_FILE).toObjectNode()
        schema.updateReferencesFor(listOf(schemaPath.toInternalRefMapping()))
        schema.removeAdditionalAndUnevaluatedProperties()
        schemaNode.setAll<JsonNode>(schema)
    }

    private fun String.toInternalRefMapping() = RefMapping("#", "#/$this")
}
