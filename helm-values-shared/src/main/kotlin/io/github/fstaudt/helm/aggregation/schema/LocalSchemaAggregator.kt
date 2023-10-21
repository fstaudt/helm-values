package io.github.fstaudt.helm.aggregation.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.removeAdditionalAndUnevaluatedProperties
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.toObjectNode
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.DEFS
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.RefMapping
import io.github.fstaudt.helm.model.RefMapping.Companion.updateReferencesFor
import java.io.File

/**
 * Aggregator for JSON schemas of current chart and locally stored dependencies
 */
class LocalSchemaAggregator(private val chartDir: File, private val schemaLocator: SchemaLocator) {
    companion object {
        const val LOCAL = "local"
    }

    fun aggregateFor(chart: Chart, jsonSchema: ObjectNode) {
        jsonSchema.aggregateCurrentChartSchema()
        jsonSchema.aggregateLocallyStoredSchemasFor(chart)
    }

    private fun ObjectNode.aggregateCurrentChartSchema() {
        File(chartDir, HELM_SCHEMA_FILE).takeIf { it.exists() }?.let {
            allOf().add(objectNode().put("\$ref", "#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE"))
            val schemaNode = objectNode(DEFS).objectNode(LOCAL).objectNode(HELM_SCHEMA_FILE)
            val schemaPath = "#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE"
            val schema = it.toObjectNode()
            schema.updateReferencesFor(listOf(RefMapping("#", schemaPath)))
            schema.removeAdditionalAndUnevaluatedProperties()
            schemaNode.setAll<JsonNode>(schema)
        }
    }

    private fun ObjectNode.aggregateLocallyStoredSchemasFor(chart: Chart) {
        updateReferencesFor(chart.dependencies.toLocallyStoredRefMappings())
        chart.dependencies.filter { it.isStoredLocally() }.forEach {
            aggregateLocalSchema(it, schemaLocator.aggregatedSchemaFor(it))
        }

    }

    private fun List<ChartDependency>.toLocallyStoredRefMappings() = mapNotNull { it.toLocallyStoredRefMapping() }
    private fun ChartDependency.toLocallyStoredRefMapping(): RefMapping? {
        return takeIf { it.isStoredLocally() }?.let {
            RefMapping(
                "../../$name/${sanitizedVersion()}/$VALUES_SCHEMA_FILE",
                "#/$DEFS/local/$name/$AGGREGATED_SCHEMA_FILE")
        }
    }

    private fun ObjectNode.aggregateLocalSchema(dependency: ChartDependency, schemaFile: File) {
        val schemaNode = objectNode(DEFS).objectNode(LOCAL).objectNode(dependency.name).objectNode(schemaFile.name)
        val schemaPath = "#/$DEFS/$LOCAL/${dependency.name}/${schemaFile.name}"
        val schema = schemaFile.toObjectNode()
        schema.updateReferencesFor(listOf(RefMapping("#", schemaPath)))
        schema.removeAdditionalAndUnevaluatedProperties()
        schemaNode.setAll<JsonNode>(schema)
    }
}
