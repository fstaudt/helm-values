package io.github.fstaudt.helm.aggregation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ADDITIONAL_PROPERTIES
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.UNEVALUATED_PROPERTIES
import io.github.fstaudt.helm.JsonSchemaConstants.NEW_LINE
import io.github.fstaudt.helm.JsonSchemaGenerator
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_DESCRIPTION
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.ObjectNodeExtensions.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.global
import io.github.fstaudt.helm.ObjectNodeExtensions.props
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.aggregation.schema.DownloadedSchemaAggregator
import io.github.fstaudt.helm.aggregation.schema.ExtractedSchemaAggregator
import io.github.fstaudt.helm.aggregation.schema.LocalSchemaAggregator
import io.github.fstaudt.helm.aggregation.values.RequiredPropertyCleaner
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.io.File
import java.net.URI

class JsonSchemaAggregator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    schemaLocator: SchemaLocator,
    chartDir: File,
    downloadedSchemasDir: File,
    extractsDir: File,
) {
    companion object {
        const val DEFS = "\$defs"
        const val BASE_URI = "https://helm-values.fstaudt.github.io"
        private val MISSING_NODE = MissingNode.getInstance()
    }

    private val generator = JsonSchemaGenerator(repositoryMappings, null)
    private val downloadedSchemaAggregator = DownloadedSchemaAggregator(repositoryMappings, downloadedSchemasDir)
    private val extractedSchemaAggregator = ExtractedSchemaAggregator(repositoryMappings, extractsDir)
    private val localSchemaAggregator = LocalSchemaAggregator(chartDir, schemaLocator)
    private val requiredPropertyCleaner = RequiredPropertyCleaner(extractsDir)

    fun aggregate(chart: Chart, valuesJsonPatch: JsonPatch?, aggregatedValuesJsonPatch: JsonPatch?): JsonNode {
        val jsonSchema = generator.generateValuesJsonSchema(chart, valuesJsonPatch)
        jsonSchema.put(ID, "$BASE_URI/${chart.name}/${chart.version}/$AGGREGATED_SCHEMA_FILE")
        jsonSchema.put("title", "Configuration for chart ${chart.name}:${chart.version}")
        downloadedSchemaAggregator.aggregateFor(chart, jsonSchema)
        extractedSchemaAggregator.aggregateFor(chart, jsonSchema)
        localSchemaAggregator.aggregateFor(chart, jsonSchema)
        jsonSchema.removeInvalidRefs()
        requiredPropertyCleaner.discardRequiredPropertiesFor(chart, jsonSchema)
        jsonSchema.addGlobalPropertiesDescriptionFor(chart)
        jsonSchema.put(UNEVALUATED_PROPERTIES, false)
        jsonSchema.put(ADDITIONAL_PROPERTIES, false)
        return aggregatedValuesJsonPatch?.apply(jsonSchema) ?: jsonSchema
    }

    private fun ObjectNode.addGlobalPropertiesDescriptionFor(chart: Chart) {
        val dependencyLabels = chart.dependencies.joinToString("") { "$NEW_LINE- ${it.fullName()}" }
        val htmlDependencyLabels = chart.dependencies.joinToString("", "<ul>", "</ul>") {
            if (repositoryMappings.containsKey(it.repository)) {
                "<li><a href='${it.fullUri()}'>${it.fullName()}</a></li>"
            } else {
                "<li>${it.fullName()}</li>"
            }
        }
        props().global().allOf().also { allOf ->
            allOf.removeAll { it["title"]?.textValue()?.startsWith(GLOBAL_VALUES_TITLE) ?: false }
        }
        props().global().allOf().add(
            objectNode()
                .put("title", "$GLOBAL_VALUES_TITLE ${chart.name}:${chart.version}")
                .put("description", "$NEW_LINE $GLOBAL_VALUES_DESCRIPTION: $dependencyLabels")
                .put("x-intellij-html-description", "<br>$GLOBAL_VALUES_DESCRIPTION: $htmlDependencyLabels")
        )
    }

    private fun ChartDependency.fullUri() = repositoryMappings[repository]?.let { URI("${it.baseUri}/$name/$version") }

    private fun ChartDependency.fullName(): String {
        return if (isStoredLocally()) {
            "$name${version?.let { ":$it" } ?: ""}"
        } else {
            "${repository?.let { "$it/" } ?: ""}$name${version?.let { ":$it" } ?: ""}"
        }
    }

    private fun ObjectNode.removeInvalidRefs() {
        findParents(REF).forEach { parent ->
            val pointer = parent[REF].textValue().removePrefix("#")
            if (runCatching { at(pointer) }.getOrDefault(MISSING_NODE).isMissingNode) {
                (parent as ObjectNode).put("_comment", "removed invalid $REF ${parent[REF].textValue()}")
                parent.remove(REF)
            }
        }
    }
}
