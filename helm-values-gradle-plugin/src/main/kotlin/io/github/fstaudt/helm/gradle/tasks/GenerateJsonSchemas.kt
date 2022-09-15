package io.github.fstaudt.helm.gradle.tasks

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaGenerator
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GENERATION_DIR
import io.github.fstaudt.helm.PATCH_GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
open class GenerateJsonSchemas : DefaultTask() {
    companion object {
        const val GENERATE_JSON_SCHEMAS = "generateJsonSchemas"
    }

    @Nested
    lateinit var extension: HelmValuesExtension

    @InputFile
    @SkipWhenEmpty
    @PathSensitive(RELATIVE)
    var chartFile: File? = null

    @InputFile
    @Optional
    @PathSensitive(RELATIVE)
    var patchValuesFile: File? = File(project.projectDir, PATCH_VALUES_SCHEMA_FILE).takeIf { it.exists() }

    @InputFile
    @Optional
    @PathSensitive(RELATIVE)
    var patchGlobalValuesFile: File? = File(project.projectDir, PATCH_GLOBAL_VALUES_SCHEMA_FILE).takeIf { it.exists() }

    @OutputDirectory
    val generatedSchemaDir = File(project.buildDir, "$HELM_VALUES/$GENERATION_DIR")

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
    fun generate() {
        val chart = chartFile?.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        extension.publishedVersion?.let { chart.version = it }
        val repository = extension.publicationRepository()
        val generator = JsonSchemaGenerator(extension.repositoryMappings, repository)
        generateValuesSchemaFile(chart, repository, generator)
        generateGlobalValuesSchemaFile(chart, repository, generator)
    }

    private fun generateValuesSchemaFile(chart: Chart, repository: JsonSchemaRepository, generator: JsonSchemaGenerator) {
        val jsonPatch = patchValuesFile?.let { JsonPatch.fromJson(jsonMapper.readTree(it)) }
        val jsonSchema = generator.generateValuesJsonSchema(chart, jsonPatch)
        jsonMapper.writeValue(File(generatedSchemaDir, repository.valuesSchemaFile), jsonSchema)
    }

    private fun generateGlobalValuesSchemaFile(chart: Chart, repository: JsonSchemaRepository, generator: JsonSchemaGenerator) {
        val jsonPatch = patchGlobalValuesFile?.let { JsonPatch.fromJson(jsonMapper.readTree(it)) }
        val jsonSchema = generator.generateGlobalValuesJsonSchema(chart, jsonPatch)
        jsonMapper.writeValue(File(generatedSchemaDir, repository.globalValuesSchemaFile), jsonSchema)
    }
}
