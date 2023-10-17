package io.github.fstaudt.helm.aggregation.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaAggregator.Companion.DEFS
import io.github.fstaudt.helm.NEW_LINE
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.global
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.globalOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.props
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.propsOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.removeAdditionalAndUnevaluatedProperties
import io.github.fstaudt.helm.ObjectNodeExtensions.Companion.toObjectNode
import io.github.fstaudt.helm.model.RefMapping
import io.github.fstaudt.helm.model.RefMapping.Companion.updateReferencesFor
import java.io.File

/**
 * Aggregator for JSON schemas of dependencies extracted from dependency archives
 */
class ExtractedSchemaAggregator(private val extractedSchemasDir: File) {
    companion object {
        const val EXTRACTED_GLOBAL_VALUES_TITLE = "Aggregated global values for"
        const val EXTRACTS = "extracts"
    }

    fun aggregateFor(jsonSchema: ObjectNode) {
        jsonSchema.setExtractedDependencyReferencesFrom(extractedSchemasDir,
            "#/$DEFS/$EXTRACTS",
            jsonSchema)
    }

    private fun ObjectNode.setExtractedDependencyReferencesFrom(
        schemasDir: File,
        refPrefix: String,
        jsonSchema: ObjectNode
    ) {
        schemasDir.listFiles { file -> file.isDirectory }?.forEach { file ->
            val ref = "$refPrefix/${file.name}"
            with(props().objectNode(file.name)) {
                if (file.containsFile(HELM_SCHEMA_FILE)) {
                    put("\$ref", "$ref/$HELM_SCHEMA_FILE")
                    jsonSchema.aggregateExtractedSchemaFor(file, "$ref/$HELM_SCHEMA_FILE".removePrefix("#/"))
                }
                setExtractedDependencyReferencesFrom(file, ref, jsonSchema)
                propsOrNull()?.globalOrNull()?.let {
                    addGlobalPropertiesDescriptionFor(ref.removePrefix("#/$DEFS/${EXTRACTS}/"))
                }
                if (has("\$ref") && size() > 1) {
                    allOf().add(objectNode().set("\$ref", remove("\$ref")) as JsonNode)
                }
            }
            addGlobalPropertiesFrom(file, refPrefix)
        }
    }

    private fun ObjectNode.addGlobalPropertiesDescriptionFor(dependencyName: String) {
        props().global().allOf().add(
            objectNode()
                .put("title", "$EXTRACTED_GLOBAL_VALUES_TITLE $dependencyName dependency")
                .put("description", NEW_LINE)
        )
    }

    private fun ObjectNode.addGlobalPropertiesFrom(schemasDir: File, refPrefix: String) {
        if (schemasDir.containsFile(HELM_SCHEMA_FILE)) {
            val ref = "$refPrefix/${schemasDir.name}/$HELM_SCHEMA_FILE/properties/global"
            props().global().allOf().add(objectNode().put("\$ref", ref))
        }
        if (schemasDir.hasSubDirectories()) {
            schemasDir.listFiles()?.forEach {
                addGlobalPropertiesFrom(it, "$refPrefix/${schemasDir.name}")
            }
        }
    }

    private fun ObjectNode.aggregateExtractedSchemaFor(schemaDir: File, schemaPath: String) {
        val schemaNode = schemaPath.split("/").filter { it.isNotBlank() }
            .fold(this) { node, s -> node.objectNode(s) }
        val schema = File(schemaDir, HELM_SCHEMA_FILE).toObjectNode()
        schema.updateReferencesFor(listOf(schemaPath.toInternalRefMapping()))
        schema.removeAdditionalAndUnevaluatedProperties()
        schemaNode.setAll<JsonNode>(schema)
    }

    private fun File.hasSubDirectories() = listFiles { file -> file.isDirectory }?.any() ?: false
    private fun File.containsFile(fileName: String) = listFiles { file -> file.name == fileName }?.any() ?: false
    private fun String.toInternalRefMapping() = RefMapping("#", "#/$this")
}
