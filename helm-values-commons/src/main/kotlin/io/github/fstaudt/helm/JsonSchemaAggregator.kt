package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
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

    fun aggregate(chart: Chart, jsonPatch: JsonPatch?): JsonNode {
        val jsonSchema = chart.toAggregatedValuesJsonSchema()
        jsonSchema.put("additionalProperties", false)
        val properties = jsonSchema.objectNode("properties")
        val globalProperties = properties.objectNode("global")
        globalProperties.put("additionalProperties", false)
        properties.setExtractedDependencyRefsFrom(extractSchemasDir, extractSchemasDir.name)
        chart.dependencies.forEach { dependency ->
            repositoryMappings[dependency.repository]?.let { repository ->
                val ref = "${downloadSchemasDir.name}/${dependency.aliasOrName()}/${repository.valuesSchemaFile}"
                properties.objectNode(dependency.aliasOrName()).put("\$ref", ref)
                val globalRef = "${downloadSchemasDir.name}/${dependency.aliasOrName()}/${repository.globalValuesSchemaFile}"
                globalProperties.allOf().add(ObjectNode(nodeFactory).put("\$ref", globalRef))
            }
            dependency.condition?.toPropertiesObjectNodeIn(jsonSchema)
                ?.put("title", "Enable ${dependency.aliasOrName()} dependency (${dependency.fullName()})")
                ?.put("description", EMPTY)
                ?.put("type", "boolean")
        }
        return jsonPatch?.apply(jsonSchema) ?: jsonSchema
    }

    private fun Chart.toAggregatedValuesJsonSchema(): ObjectNode {
        return ObjectNode(nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "$name/$version/${AGGREGATED_SCHEMA_FILE}")
            .put("title", "Configuration for chart $name/$version")
            .put("description", EMPTY)
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

    private fun ObjectNode.allOf(): ArrayNode {
        return get("allOf") as? ArrayNode ?: ArrayNode(nodeFactory).also { set<ObjectNode>("allOf", it) }
    }

    private fun String.toPropertiesObjectNodeIn(jsonSchema: ObjectNode): ObjectNode {
        return split('.').fold(jsonSchema) { node: ObjectNode, property: String ->
            node.objectNode("properties").objectNode(property)
        }
    }
}
