package io.github.fstaudt.helm.idea.providers

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion.SCHEMA_7
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.CHART_METADATA_FILE
import java.io.File

abstract class BaseJsonSchemaProvider(jsonSchemasDir: File) : JsonSchemaFileProvider {
    companion object {
        private val yamlMapper = ObjectMapper(YAMLFactory()).also {
            it.registerModule(KotlinModule.Builder().build())
            it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    private val chartMetadata = File(jsonSchemasDir, CHART_METADATA_FILE).inputStream().use {
        yamlMapper.readValue(it, HelmChartMetadata::class.java)
    }

    override fun isAvailable(file: VirtualFile): Boolean {
        return "${file.parent.toNioPath()}" == "${chartMetadata.dir}"
    }

    override fun getSchemaVersion() = SCHEMA_7
    override fun getSchemaType() = SchemaType.userSchema
}
