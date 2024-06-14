package io.github.fstaudt.helm.gradle.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.JsonSchemaFactory.getInstance
import com.networknt.schema.PathType.JSON_PATH
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion.VersionFlag.V202012
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters.None
import java.io.File
import java.util.*
import java.util.Locale.ENGLISH

abstract class JsonMapper : BuildService<None> {
    private val objectMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(INDENT_OUTPUT)
    }

    private val factory = jsonSchemaFactory()
    private fun jsonSchemaFactory(): JsonSchemaFactory {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(ENGLISH)
            return JsonSchemaFactory.builder(getInstance(V202012)).objectMapper(objectMapper).build()
        } finally {
            Locale.setDefault(locale)
        }
    }

    fun patchFrom(jsonFile: Property<File>): JsonPatch? {
        return jsonFile.get().takeIf { it.exists() }?.toJsonPatch()
    }

    fun schemaFrom(aggregatedSchemaFile: Provider<RegularFile>): JsonSchema {
        val config = SchemaValidatorsConfig().also { it.pathType = JSON_PATH }
        return aggregatedSchemaFile.get().asFile.inputStream().use {
            factory.getSchema(objectMapper.readTree(it), config)
        }
    }

    fun writeTo(aggregatedSchemaFile: Provider<RegularFile>, content: JsonNode) {
        objectMapper.writeValue(aggregatedSchemaFile.get().asFile, content)
    }

    private fun File.toJsonPatch() = let { JsonPatch.fromJson(objectMapper.readTree(it)) }
}
