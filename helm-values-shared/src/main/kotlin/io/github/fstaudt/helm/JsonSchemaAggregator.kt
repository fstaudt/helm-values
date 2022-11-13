package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_DESCRIPTION
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.global
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.properties
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
        const val EXTRACTED_GLOBAL_VALUES_TITLE = "Aggregated global values for"
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
        jsonSchema.updateDownloadedDependencyReferencesFor(chart)
        jsonSchema.removeGeneratedGlobalDescription()
        jsonSchema.setExtractedDependencyReferencesFrom(extractSchemasDir, extractSchemasDir.name)
        jsonSchema.addGlobalPropertiesDescriptionFor(chart)
        return aggregatedJsonPatch?.apply(jsonSchema) ?: jsonSchema
    }

    private fun ObjectNode.updateDownloadedDependencyReferencesFor(chart: Chart) {
        val refMappings = chart.dependencies.mapNotNull { it.toRefMapping() }
        findParents("\$ref").forEach { parent ->
            val ref = parent.get("\$ref")
            refMappings.firstOrNull { it.matches(ref) }?.let {
                (parent as ObjectNode).replace("\$ref", it.map(ref))
            }
        }
    }

    private fun ObjectNode.removeGeneratedGlobalDescription() {
        properties().global().allOf().also { allOf ->
            allOf.removeAll { it.get("title")?.textValue()?.startsWith(GLOBAL_VALUES_TITLE) ?: false }
        }
    }

    private fun ObjectNode.setExtractedDependencyReferencesFrom(schemasDir: File, refPrefix: String) {
        with(properties()) {
            schemasDir.listFiles(FileFilter { it.isDirectory })?.forEach {
                with(objectNode(it.name)) {
                    if (it.containsFile(HELM_SCHEMA_FILE)) {
                        put("\$ref", "$refPrefix/${it.name}/$HELM_SCHEMA_FILE")
                    }
                    setExtractedDependencyReferencesFrom(it, "$refPrefix/${it.name}")
                    properties().global().put("additionalProperties", false)
                    addGlobalPropertiesDescriptionFor("$refPrefix/${it.name}".removePrefix("${extractSchemasDir.name}/"))
                }
                addGlobalPropertiesFrom(it, refPrefix)
            }
        }
    }

    private fun ObjectNode.addGlobalPropertiesFrom(schemasDir: File, refPrefix: String) {
        if (schemasDir.containsFile(HELM_SCHEMA_FILE)) {
            val ref = "$refPrefix/${schemasDir.name}/$HELM_SCHEMA_FILE#/properties/global"
            global().allOf().add(ObjectNode(nodeFactory).put("\$ref", ref))
        }
        if (schemasDir.hasSubDirectories()) {
            schemasDir.listFiles()?.forEach {
                addGlobalPropertiesFrom(it, "$refPrefix/${schemasDir.name}")
            }
        }
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
        properties().global().allOf().add(
            ObjectNode(nodeFactory)
                .put("title", "$GLOBAL_VALUES_TITLE ${chart.name}:${chart.version}")
                .put("description", "$NEW_LINE $GLOBAL_VALUES_DESCRIPTION: $dependencyLabels")
                .put("x-intellij-html-description", "<br>$GLOBAL_VALUES_DESCRIPTION: $htmlDependencyLabels")
        )
    }

    private fun ObjectNode.addGlobalPropertiesDescriptionFor(dependencyName: String) {
        properties().global().allOf().add(
            ObjectNode(nodeFactory)
                .put("title", "$EXTRACTED_GLOBAL_VALUES_TITLE $dependencyName dependency")
                .put("description", NEW_LINE)
        )
    }

    private fun ChartDependency.fullUri() = repositoryMappings[repository]?.let { "${it.baseUri}/$name/$version" }

    private fun File.hasSubDirectories() = listFiles(FileFilter { it.isDirectory })?.any() ?: false
    private fun File.containsFile(fileName: String) = listFiles(FileFilter { it.name == fileName })?.any() ?: false

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
