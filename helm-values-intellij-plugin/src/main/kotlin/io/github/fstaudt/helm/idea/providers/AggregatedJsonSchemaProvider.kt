package io.github.fstaudt.helm.idea.providers

import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VirtualFile
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import java.io.File

class AggregatedJsonSchemaProvider(private val jsonSchemasDir: File) : BaseJsonSchemaProvider(jsonSchemasDir) {
    private val jsonSchemaFile = findFileByIoFile(File(jsonSchemasDir, AGGREGATED_SCHEMA_FILE), false)
    override fun isAvailable(file: VirtualFile) = file.name == "values.yaml" && super.isAvailable(file)
    override fun getName() = "aggregation for chart ${jsonSchemasDir.name}"
    override fun getSchemaFile() = jsonSchemaFile
}
