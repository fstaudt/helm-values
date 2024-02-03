package io.github.fstaudt.helm.gradle.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.JsonSchemaFactory.builder
import com.networknt.schema.JsonSchemaFactory.getInstance
import com.networknt.schema.PathType.JSON_PATH
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion.VersionFlag.V202012
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.exceptions.ValuesValidationException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.*
import java.util.Locale.ENGLISH

@DisableCachingByDefault(because = "no output")
open class ValidateHelmValues : DefaultTask() {
    companion object {
        const val VALIDATE_HELM_VALUES = "validateHelmValues"
    }

    @Nested
    lateinit var extension: HelmValuesExtension

    @InputFile
    @PathSensitive(RELATIVE)
    var aggregatedSchemaFile: File = File(project.buildDir, "$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")

    @InputFile
    @SkipWhenEmpty
    @PathSensitive(RELATIVE)
    var valuesFile: File? = null

    @Internal
    protected val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Internal
    protected val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(INDENT_OUTPUT)
    }

    @Internal
    protected val factory: JsonSchemaFactory = jsonSchemaFactory()

    @TaskAction
    fun aggregate() {
        val config = SchemaValidatorsConfig().also { it.pathType = JSON_PATH }
        val schema: JsonSchema = aggregatedSchemaFile.inputStream().use {
            factory.getSchema(jsonMapper.readTree(it), config)
        }
        val jsonNode: JsonNode = valuesFile!!.inputStream().use { yamlMapper.readTree(it) }
        val validationMessages = schema.validate(jsonNode)
        if (validationMessages.isNotEmpty()) {
            throw ValuesValidationException(valuesFile!!, validationMessages)
        }
    }

    private fun jsonSchemaFactory(): JsonSchemaFactory {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(ENGLISH)
            return builder(getInstance(V202012)).objectMapper(jsonMapper).build()
        } finally {
            Locale.setDefault(locale)
        }
    }
}
