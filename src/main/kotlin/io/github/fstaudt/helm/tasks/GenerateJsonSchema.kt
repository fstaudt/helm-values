package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.exceptions.RepositoryNotFoundException
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.RepositoryMapping
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI

@CacheableTask
@Suppress("NestedLambdaShadowedImplicitParameter")
open class GenerateJsonSchema : DefaultTask() {
    companion object {
        const val GENERATE_JSON_SCHEMA = "generateJsonSchema"
        const val GENERATED = "$HELM_VALUES/generated"
    }

    @OutputDirectory
    val generatedSchemaFolder = File(project.buildDir, GENERATED)

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @InputFile
    @PathSensitive(RELATIVE)
    lateinit var chartFile: File

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(INDENT_OUTPUT)
    }
    private val nodeFactory = jsonMapper.nodeFactory

    @TaskAction
    fun generate() {
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        generateValuesSchemaFile(chart)
        generateGlobalValuesSchemaFile(chart)
    }

    private fun generateValuesSchemaFile(chart: Chart) {
        val jsonSchema = chart.toJsonSchema(VALUES_SCHEMA_FILE)
        jsonSchema.objectNode("properties").objectNode("global").put("\$ref", GLOBAL_VALUES_SCHEMA_FILE)
        chart.dependencies.forEach { dependency ->
            extension.repositoryMappings[dependency.repository]?.let {
                val ref = "${it.baseUri}/${dependency.name}/${dependency.version}/$VALUES_SCHEMA_FILE".toRelativeUri()
                jsonSchema.objectNode("properties").objectNode(dependency.aliasOrName()).put("\$ref", ref)
                dependency.condition?.toPropertiesObjectNodeIn(jsonSchema)
                    ?.put("title", "Enable ${dependency.aliasOrName()} dependency (${dependency.fullName()})")
                    ?.put("description", "\\n")
                    ?.put("type", "boolean")
            }
        }
        jsonMapper.writeValue(File(generatedSchemaFolder, VALUES_SCHEMA_FILE), jsonSchema)
    }

    private fun generateGlobalValuesSchemaFile(chart: Chart) {
        val jsonSchema = chart.toJsonSchema(GLOBAL_VALUES_SCHEMA_FILE)
        jsonSchema.allOf().let { allOf ->
            chart.dependencies.forEach { dependency ->
                extension.repositoryMappings[dependency.repository]?.let {
                    val ref = "${it.baseUri}/${dependency.name}/${dependency.version}/$GLOBAL_VALUES_SCHEMA_FILE"
                        .toRelativeUri()
                    allOf.add(ObjectNode(nodeFactory).put("\$ref", ref))
                }
            }
        }
        jsonMapper.writeValue(File(generatedSchemaFolder, GLOBAL_VALUES_SCHEMA_FILE), jsonSchema)
    }

    private fun Chart.toJsonSchema(fileName: String): ObjectNode {
        return ObjectNode(jsonMapper.nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "${targetRepositoryMapping().baseUri}/$name/$version/$fileName")
            .put("title", "Configuration for chart ${extension.targetRepository}/$name/$version")
            .put("description", "\\n")
    }

    private fun ObjectNode.objectNode(propertyName: String): ObjectNode {
        return get(propertyName) as? ObjectNode ?: ObjectNode(nodeFactory).also { set<ObjectNode>(propertyName, it) }
    }

    private fun ObjectNode.allOf(): ArrayNode {
        return get("allOf") as? ArrayNode ?: ArrayNode(nodeFactory).also { set<ObjectNode>("allOf", it) }
    }

    private fun String.toRelativeUri(): String {
        return targetRepositoryMapping().let {
            val uri = URI(this)
            val targetUri = URI(it.baseUri)
            when {
                uri.host == targetUri.host && uri.path.startsWith(targetUri.path) ->
                    uri.path.replace(targetUri.path, "../..")
                uri.host == targetUri.host ->
                    "../..${"/..".repeat(targetUri.path.count { it == '/' })}${uri.path}"
                else -> this
            }
        } ?: this
    }

    private fun String.toPropertiesObjectNodeIn(jsonSchema: ObjectNode): ObjectNode {
        return split('.').fold(jsonSchema) { node: ObjectNode, property: String ->
            node.objectNode("properties").objectNode(property)
        }
    }

    private fun targetRepositoryMapping(): RepositoryMapping {
        return extension.repositoryMappings[extension.targetRepository]
            ?: throw RepositoryNotFoundException(extension.targetRepository)
    }
}
