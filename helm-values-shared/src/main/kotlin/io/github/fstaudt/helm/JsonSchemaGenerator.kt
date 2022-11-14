package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.global
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.properties
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.net.URI

class JsonSchemaGenerator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val publicationRepository: JsonSchemaRepository,
) {

    companion object {
        const val GENERATION_DIR = "generated"
        const val GLOBAL_VALUES_TITLE = "Aggregated global values for chart"
        const val GLOBAL_VALUES_DESCRIPTION = "Aggregation of global values of chart and its dependencies"
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }
        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory
    }

    fun generatePackagedValuesJsonSchema(chart: Chart, jsonPatch: JsonPatch?): ObjectNode {
        val jsonSchema = chart.toPackagedValuesJsonSchema()
        jsonSchema.properties().global().put("\$ref", "$AGGREGATED_SCHEMA_FILE#/properties/global")
        jsonSchema.properties().objectNode(chart.name).put("\$ref", AGGREGATED_SCHEMA_FILE)
        jsonSchema.put("additionalProperties", false)
        return (jsonPatch?.apply(jsonSchema) as? ObjectNode) ?: jsonSchema
    }

    fun generateValuesJsonSchema(chart: Chart, jsonPatch: JsonPatch?): ObjectNode {
        val jsonSchema = chart.toValuesJsonSchema()
        jsonSchema.properties().global().putGlobalProperties(chart)
        jsonSchema.put("additionalProperties", false)
        chart.dependencies.filter { it.version != null }.forEach { dep ->
            repositoryMappings[dep.repository]?.let {
                val ref = "${it.baseUri}/${dep.name}/${dep.version}/${it.valuesSchemaFile}".toRelativeUri()
                jsonSchema.properties().objectNode(dep.aliasOrName()).put("\$ref", ref)
            }
        }
        chart.dependencies.forEach { dep ->
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
                allOf.add(globalPropertiesDescriptionFor(chart))
            }
        }
    }

    private fun globalPropertiesDescriptionFor(chart: Chart): ObjectNode {
        val dependencies = chart.dependencies.filter { repositoryMappings.containsKey(it.repository) }
        val dependencyLabels = dependencies.joinToString("") { "$NEW_LINE- ${it.fullName()}" }
        val htmlDependencyLabels = dependencies.joinToString("", "<ul>", "</ul>") {
            "<li><a href='${it.fullUri()}'>${it.fullName()}</a></li>"
        }
        return ObjectNode(nodeFactory)
            .put("title", "$GLOBAL_VALUES_TITLE ${chart.name}:${chart.version}")
            .put("description", "$NEW_LINE $GLOBAL_VALUES_DESCRIPTION: $dependencyLabels")
            .put("x-intellij-html-description", "<br>$GLOBAL_VALUES_DESCRIPTION: $htmlDependencyLabels")
    }

    private fun ChartDependency.fullUri() = repositoryMappings[repository]?.let { "${it.baseUri}/$name/$version" }

    private fun Chart.toValuesJsonSchema(): ObjectNode {
        return ObjectNode(nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "${publicationRepository.baseUri}/$name/$version/${publicationRepository.valuesSchemaFile}")
            .put("title", "Configuration for chart $name:$version")
            .put("description", NEW_LINE)
    }

    private fun Chart.toPackagedValuesJsonSchema(): ObjectNode {
        return ObjectNode(nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "${publicationRepository.baseUri}/$name/$version/$PACKAGED_SCHEMA_FILE")
            .put("title", "Configuration for packaged chart $name:$version")
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

    private fun String.toPropertiesObjectNodeIn(jsonSchema: ObjectNode): ObjectNode {
        return split('.').fold(jsonSchema) { node: ObjectNode, property: String ->
            node.properties().objectNode(property)
        }
    }
}
