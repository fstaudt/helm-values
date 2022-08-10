package io.github.fstaudt.helm.gradle.tasks

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaAggregator
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.tasks.DownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.gradle.tasks.ExtractJsonSchemas.Companion.EXTRACT
import io.github.fstaudt.helm.model.Chart
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
open class AggregateJsonSchema : DefaultTask() {
    companion object {
        const val AGGREGATE_JSON_SCHEMA = "aggregateJsonSchema"
        const val AGGREGATED_SCHEMA_FILE = "aggregated-values.schema.json"
        const val PATCH_AGGREGATED_SCHEMA_FILE = "patch-aggregated-values.schema.json"
    }

    @Nested
    lateinit var extension: HelmValuesExtension

    @InputFile
    @SkipWhenEmpty
    @PathSensitive(RELATIVE)
    var chartFile: File? = null

    @InputDirectory
    @PathSensitive(RELATIVE)
    val downloadSchemasDir = File(project.buildDir, "$HELM_VALUES/$DOWNLOADS")

    @InputDirectory
    @PathSensitive(RELATIVE)
    val extractSchemasDir = File(project.buildDir, "$HELM_VALUES/$EXTRACT")

    @InputFile
    @Optional
    @PathSensitive(RELATIVE)
    var patchFile: File? = File(project.projectDir, PATCH_AGGREGATED_SCHEMA_FILE).takeIf { it.exists() }

    @OutputFile
    val aggregatedSchemaFile: File = File(project.buildDir, "$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")


    @Internal
    protected val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Internal
    protected val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(SerializationFeature.INDENT_OUTPUT)
    }

    @TaskAction
    fun aggregate() {
        val aggregator = JsonSchemaAggregator(extension.repositoryMappings, downloadSchemasDir, extractSchemasDir)
        val chart = chartFile?.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        val jsonPatch = patchFile?.let { JsonPatch.fromJson(jsonMapper.readTree(it)) }
        val jsonSchema = aggregator.aggregate(chart, jsonPatch)
        jsonMapper.writeValue(aggregatedSchemaFile, jsonSchema)
    }
}