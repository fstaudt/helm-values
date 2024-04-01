package io.github.fstaudt.helm.idea.providers

import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType.userSchema
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion.SCHEMA_7
import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.Mappers.chartMetadataMapper
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.CHART_METADATA_FILE
import java.io.File

class AggregatedJsonSchemaProvider(private val jsonSchemasDir: File) : JsonSchemaFileProvider {
    private val chartMetadata = File(jsonSchemasDir, CHART_METADATA_FILE).inputStream().use {
        chartMetadataMapper.readValue(it, HelmChartMetadata::class.java)
    }

    private val jsonSchemaFile = findFileByIoFile(File(jsonSchemasDir, AGGREGATED_SCHEMA_FILE), false)
    override fun getSchemaVersion() = SCHEMA_7
    override fun getSchemaType() = userSchema

    override fun isAvailable(file: VirtualFile): Boolean {
        return file.name == "values.yaml" && file.parent?.toPath() == "${chartMetadata.dir}"
    }

    override fun getName() = "aggregation for chart ${jsonSchemasDir.name}"
    override fun getSchemaFile() = jsonSchemaFile
    private fun VirtualFile.toPath() = runCatching { "${toNioPath()}" }.getOrDefault("")
}
