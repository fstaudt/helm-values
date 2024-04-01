package io.github.fstaudt.helm.aggregation.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.ObjectNodeExtensions.isInternalReference
import io.github.fstaudt.helm.ObjectNodeExtensions.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.removeAdditionalAndUnevaluatedProperties
import io.github.fstaudt.helm.ObjectNodeExtensions.splitNotBlanks
import io.github.fstaudt.helm.ObjectNodeExtensions.toObjectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.toUriFrom
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.DEFS
import io.github.fstaudt.helm.aggregation.schema.ImportValuesAggregator.addImportValueReferencesFor
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.model.RefMapping
import io.github.fstaudt.helm.model.RefMapping.Companion.updateReferencesFor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

/**
 * Aggregator for JSON schemas of dependencies downloaded from JSON schema repositories
 */
class DownloadedSchemaAggregator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val downloadSchemasDir: File,
) {

    companion object {
        const val DOWNLOADS = "downloads"
        private val logger: Logger = LoggerFactory.getLogger(DownloadedSchemaAggregator::class.java)
    }

    fun aggregateFor(chart: Chart, jsonSchema: ObjectNode) {
        jsonSchema.updateReferencesFor(chart.dependencies.toDownloadedRefMappings())
        chart.dependencies.filter { repositoryMappings.contains(it.repository) }.forEach {
            jsonSchema.aggregateDownloadedSchemaFor(it.valuesSchemaUri())
            jsonSchema.addImportValueReferencesFor(it.importValues, schemaPathFor(it.valuesSchemaUri()))
        }
    }

    private fun ObjectNode.aggregateDownloadedSchemaFor(schemaUri: URI): String {
        val schemaNode = schemaNodeFor(schemaUri)
        val schemaPath = schemaPathFor(schemaUri)
        if (schemaNode.isEmpty) {
            val schema = File(downloadSchemasDir, schemaUri.path).toObjectNode()
            schema.findParents(REF).forEach { parent ->
                val ref = parent[REF]
                if (ref.isInternalReference()) {
                    val refMapping = RefMapping("#", schemaPath)
                    (parent as ObjectNode).replace(REF, refMapping.map(ref))
                } else {
                    try {
                        val refUri = ref.toUriFrom(schemaUri)
                        val refPath = aggregateDownloadedSchemaFor(refUri)
                        (parent as ObjectNode).replace(REF, TextNode(refPath + (refUri.fragment ?: "")))
                    } catch (e: Exception) {
                        logger.warn("Failed to aggregate schema for ref \"${ref.textValue()}\"", e)
                    }
                }
            }
            schema.removeAdditionalAndUnevaluatedProperties()
            schemaNode.setAll<JsonNode>(schema)
        }
        return schemaPath
    }

    private fun List<ChartDependency>.toDownloadedRefMappings() = mapNotNull { it.toDownloadedRefMapping() }
    private fun ChartDependency.toDownloadedRefMapping(): RefMapping? {
        return repositoryMappings[repository]?.let {
            RefMapping(
                "${it.baseUri}/$name/$version",
                "#/$DEFS/${DOWNLOADS}${URI(it.baseUri).path}/$name/$version"
            )
        }
    }

    private fun ChartDependency.valuesSchemaUri(): URI {
        return repositoryMappings[repository]!!.let { URI("${it.baseUri}/$name/$version/${it.valuesSchemaFile}") }
    }

    private fun ObjectNode.schemaNodeFor(uri: URI): ObjectNode {
        return uri.path.splitNotBlanks("/")
            .fold(objectNode(DEFS).objectNode(DOWNLOADS)) { node, dir -> node.objectNode(dir) }
    }

    private fun schemaPathFor(uri: URI): String {
        return uri.path.splitNotBlanks("/")
            .fold("#/$DEFS/$DOWNLOADS") { basePath, dir -> "$basePath/$dir" }
    }
}
