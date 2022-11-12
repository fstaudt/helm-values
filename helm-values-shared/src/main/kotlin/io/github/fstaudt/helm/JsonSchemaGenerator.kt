package io.github.fstaudt.helm

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

    fun generateValuesJsonSchema(chart: Chart, jsonPatch: JsonPatch?): ObjectNode {
        val jsonSchema = chart.toValuesJsonSchema()
        jsonSchema.objectNode("properties").objectNode("global").putGlobalProperties(chart)
        jsonSchema.put("additionalProperties", false)
        chart.dependencies.forEach { dep ->
            repositoryMappings[dep.repository]?.let {
                val ref = "${it.baseUri}/${dep.name}/${dep.version}/${it.valuesSchemaFile}".toRelativeUri()
                jsonSchema.objectNode("properties").objectNode(dep.aliasOrName()).put("\$ref", ref)
            }
            dep.condition?.toPropertiesObjectNodeIn(jsonSchema)
                ?.put("title", "Enable ${dep.aliasOrName()} dependency (${dep.fullName()})")
                ?.put("description", NEW_LINE)
                ?.put("type", "boolean")
        }
        return (jsonPatch?.apply(jsonSchema) as? ObjectNode) ?: jsonSchema
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
            .put("description", NEW_LINE)
    }

    private fun String.toRelativeUri(): String {
        val uri = URI(this)
        val publicationUri = URI(publicationRepository.baseUri)
        return when {
            uri.host == publicationUri.host && uri.path.startsWith(publicationUri.path) ->
                uri.path.replace(publicationUri.path, "../..")

            uri.host == publicationUri.host ->
                "../..${"/..".repeat(publicationUri.path.count { it == '/' })}${uri.path}"

            else -> this
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
