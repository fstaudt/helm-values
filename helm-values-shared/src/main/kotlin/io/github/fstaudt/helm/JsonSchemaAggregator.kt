package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.DEFAULT_JSON_SCHEMA_REPOSITORY
import java.io.File
import java.io.FileFilter

class JsonSchemaAggregator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val downloadSchemasDir: File,
    private val extractSchemasDir: File,
) {
    companion object {
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }

        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory
    }

    private val generator = JsonSchemaGenerator(repositoryMappings, DEFAULT_JSON_SCHEMA_REPOSITORY)

    fun aggregate(chart: Chart, valuesJsonPatch: JsonPatch?, aggregatedJsonPatch: JsonPatch?): JsonNode {
        val jsonSchema = generator.generateValuesJsonSchema(chart, valuesJsonPatch)
        jsonSchema.put("\$id", "${chart.name}/${chart.version}/${AGGREGATED_SCHEMA_FILE}")
        jsonSchema.put("title", "Configuration for chart ${chart.name}:${chart.version}")
        jsonSchema.updateDependencyReferencesFor(chart)
        val properties = jsonSchema.objectNode("properties")
        properties.setExtractedDependencyRefsFrom(extractSchemasDir, extractSchemasDir.name)
        return aggregatedJsonPatch?.apply(jsonSchema) ?: jsonSchema
    }

    private fun ObjectNode.updateDependencyReferencesFor(chart: Chart) {
        val refMappings = chart.dependencies.mapNotNull { it.toRefMapping() }
        findParents("\$ref").forEach { parent ->
            val ref = parent.get("\$ref")
            refMappings.firstOrNull { it.matches(ref) }?.let {
                (parent as ObjectNode).replace("\$ref", it.map(ref))
            }
        }
    }

    private fun ObjectNode.setExtractedDependencyRefsFrom(extractSchemasDir: File, refPrefix: String) {
        extractSchemasDir.listFiles()?.forEach {
            with(objectNode(it.name)) {
                if (it.containsFile(HELM_SCHEMA_FILE)) {
                    put("\$ref", "$refPrefix/${it.name}/$HELM_SCHEMA_FILE")
                }
                if (it.hasSubDirectories()) {
                    objectNode("properties").setExtractedDependencyRefsFrom(it, "$refPrefix/${it.name}")
                }
            }
        }
    }

    private fun File.hasSubDirectories() = listFiles(FileFilter { it.isDirectory })?.any() ?: false
    private fun File.containsFile(fileName: String) = listFiles(FileFilter { it.name == fileName })?.any() ?: false

    private fun ObjectNode.objectNode(propertyName: String): ObjectNode {
        return get(propertyName) as? ObjectNode ?: ObjectNode(nodeFactory).also { set<ObjectNode>(propertyName, it) }
    }

    private data class RefMapping(val baseUri: String, val mappedBaseUri: String) {
        fun matches(ref: JsonNode) = ref.textValue().startsWith(baseUri)
        fun map(ref: JsonNode) = TextNode(ref.textValue().replace(baseUri, mappedBaseUri))
    }

    private fun ChartDependency.toRefMapping(): RefMapping? {
        return repositoryMappings[repository]?.let {
            RefMapping("${it.baseUri}/$name/$version", "${downloadSchemasDir.name}/${aliasOrName()}")
        }
    }
}
