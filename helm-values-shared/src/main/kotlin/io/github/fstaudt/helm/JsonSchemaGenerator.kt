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
import java.net.URI

@Suppress("NestedLambdaShadowedImplicitParameter")
class JsonSchemaGenerator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val publicationRepository: JsonSchemaRepository,
) {

    companion object {
        const val GENERATION_DIR = "generated"
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }

        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory
    }

    fun generateValuesJsonSchema(chart: Chart, jsonPatch: JsonPatch?): JsonNode {
        val jsonSchema = chart.toValuesJsonSchema()
        jsonSchema.objectNode("properties").objectNode("global").putGlobalProperties(chart)
        jsonSchema.put("additionalProperties", false)
        chart.dependencies.forEach { dependency ->
            repositoryMappings[dependency.repository]?.let {
                val ref = "${it.baseUri}/${dependency.name}/${dependency.version}/${it.valuesSchemaFile}".toRelativeUri()
                jsonSchema.objectNode("properties").objectNode(dependency.aliasOrName()).put("\$ref", ref)
            }
            dependency.condition?.toPropertiesObjectNodeIn(jsonSchema)
                ?.put("title", "Enable ${dependency.aliasOrName()} dependency (${dependency.fullName()})")
                ?.put("description", EMPTY)
                ?.put("type", "boolean")
        }
        return jsonPatch?.apply(jsonSchema) ?: jsonSchema
    }

    private fun ObjectNode.putGlobalProperties(chart: Chart) {
        put("additionalProperties", false)
        if (chart.dependencies.any { repositoryMappings.containsKey(it.repository) }) {
            allOf().let { allOf ->
                chart.dependencies.forEach { dependency ->
                    repositoryMappings[dependency.repository]?.let {
                        val refPrefix = "${it.baseUri}/${dependency.name}/${dependency.version}".toRelativeUri()
                        val ref = "$refPrefix/${it.valuesSchemaFile}#/properties/global"
                        allOf.add(ObjectNode(nodeFactory).put("\$ref", ref))
                        val globalRef = "$refPrefix/${it.globalValuesSchemaFile}"
                        allOf.add(ObjectNode(nodeFactory).put("\$ref", globalRef))
                    }
                }
            }
        }
    }

    private fun Chart.toValuesJsonSchema(): ObjectNode {
        return ObjectNode(nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "${publicationRepository.baseUri}/$name/$version/${publicationRepository.valuesSchemaFile}")
            .put("title", "Configuration for chart ${publicationRepository.baseUri}/$name/$version")
            .put("description", EMPTY)
    }

    private fun String.toRelativeUri(): String {
        return publicationRepository.let {
            val uri = URI(this)
            val publicationUri = URI(it.baseUri)
            when {
                uri.host == publicationUri.host && uri.path.startsWith(publicationUri.path) ->
                    uri.path.replace(publicationUri.path, "../..")
                uri.host == publicationUri.host ->
                    "../..${"/..".repeat(publicationUri.path.count { it == '/' })}${uri.path}"
                else -> this
            }
        }
    }

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
