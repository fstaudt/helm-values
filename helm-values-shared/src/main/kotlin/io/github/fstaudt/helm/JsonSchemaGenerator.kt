package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaConstants.GENERATOR_LABEL
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.SCHEMA
import io.github.fstaudt.helm.JsonSchemaConstants.NEW_LINE
import io.github.fstaudt.helm.JsonSchemaConstants.SCHEMA_VERSION
import io.github.fstaudt.helm.ObjectNodeExtensions.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.global
import io.github.fstaudt.helm.ObjectNodeExtensions.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.objectNodeOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.props
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.DEFAULT_JSON_SCHEMA_REPOSITORY
import java.net.URI
import java.time.OffsetDateTime.now
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.SECONDS

class JsonSchemaGenerator(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val publicationRepositoryName: String?,
) {

    private val publicationRepository = repositoryMappings[publicationRepositoryName] ?: DEFAULT_JSON_SCHEMA_REPOSITORY

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

    fun generateValuesJsonSchema(chart: Chart, jsonPatch: JsonPatch?): ObjectNode {
        val jsonSchema = chart.toValuesJsonSchema()
        jsonSchema.props().global().putGlobalProperties(chart)
        chart.dependencies.filter { it.version != null }.forEach { dep ->
            dep.repository()?.let {
                val ref = "${it.baseUri}/${dep.name}/${dep.sanitizedVersion()}/${it.valuesSchemaFile}".toRelativeUri()
                jsonSchema.props().objectNode(dep.aliasOrName()).put(REF, ref)
            }
        }
        chart.dependencies.forEach { dep ->
            dep.condition?.toPropertiesObjectNodesIn(jsonSchema)?.forEach { condition ->
                condition.put("title", "Enable ${dep.aliasOrName()} dependency (${dep.fullName()})")
                    .put("description", NEW_LINE)
                    .put("type", "boolean")
                jsonSchema.props().objectNodeOrNull(dep.aliasOrName())?.let {
                    if (it.has(REF) && it.properties().size > 1) {
                        it.allOf().add(it.objectNode().set(REF, it.remove(REF)) as JsonNode)
                    }
                }
            }
        }
        return (jsonPatch?.apply(jsonSchema) as? ObjectNode) ?: jsonSchema
    }

    private fun ObjectNode.putGlobalProperties(chart: Chart) {
        if (chart.dependencies.any { it.repository() != null }) {
            allOf().let { allOf ->
                chart.dependencies.forEach { dep ->
                    dep.repository()?.let {
                        val refPrefix = "${it.baseUri}/${dep.name}/${dep.sanitizedVersion()}".toRelativeUri()
                        val ref = "$refPrefix/${it.valuesSchemaFile}#/properties/global"
                        allOf.add(ObjectNode(nodeFactory).put(REF, ref))
                    }
                }
                allOf.add(globalPropertiesDescriptionFor(chart))
            }
        }
    }

    private fun globalPropertiesDescriptionFor(chart: Chart): ObjectNode {
        val dependencies = chart.dependencies.filter { it.repository() != null }
        val dependencyLabels = dependencies.joinToString("") { "$NEW_LINE- ${it.fullName()}" }
        val htmlDependencyLabels = dependencies.joinToString("", "<ul>", "</ul>") {
            "<li><a href='${it.fullUri()}'>${it.fullName()}</a></li>"
        }
        return ObjectNode(nodeFactory)
            .put("title", "$GLOBAL_VALUES_TITLE ${chart.name}:${chart.version}")
            .put("description", "$NEW_LINE $GLOBAL_VALUES_DESCRIPTION: $dependencyLabels")
            .put("x-intellij-html-description", "<br>$GLOBAL_VALUES_DESCRIPTION: $htmlDependencyLabels")
    }

    private fun ChartDependency.fullUri() = repository()?.let { "${it.baseUri}/$name/${sanitizedVersion()}" }

    private fun ChartDependency.fullName(): String {
        return if (isStoredLocally()) {
            "${publicationRepositoryName?.let { "$it/" } ?: ""}$name${version?.let { ":$it" } ?: ""}"
        } else {
            "${repository?.let { "$it/" } ?: ""}$name${version?.let { ":$it" } ?: ""}"
        }
    }

    private fun ChartDependency.repository(): JsonSchemaRepository? {
        return if (isStoredLocally()) publicationRepository else repositoryMappings[repository]
    }

    private fun Chart.toValuesJsonSchema(): ObjectNode {
        return ObjectNode(nodeFactory)
            .put(SCHEMA, SCHEMA_VERSION)
            .put(ID, "${publicationRepository.baseUri}/$name/$version/${publicationRepository.valuesSchemaFile}")
            .put("x-generated-by", GENERATOR_LABEL)
            .put("x-generated-at", "${now(UTC).truncatedTo(SECONDS)}")
            .put("title", "Configuration for chart ${fullName()}")
            .put("description", NEW_LINE)
    }

    private fun Chart.fullName(): String {
        return "${publicationRepositoryName?.let { "$it/" } ?: ""}$name:$version"
    }

    private fun String.toRelativeUri(): String {
        val uri = URI(this)
        val publicationUri = URI(publicationRepository.baseUri)
        return when {
            uri.host == publicationUri.host && uri.path.startsWith(publicationUri.path) ->
                uri.path.replaceFirst(publicationUri.path, "../..")

            uri.host == publicationUri.host ->
                "../..${"/..".repeat(publicationUri.path.count { it == '/' })}${uri.path}"

            else -> this
        }
    }

    private fun String.toPropertiesObjectNodesIn(jsonSchema: ObjectNode): List<ObjectNode> {
        return split(',').map {
            it.split('.').fold(jsonSchema) { node: ObjectNode, property: String ->
                node.props().objectNode(property)
            }
        }
    }
}
