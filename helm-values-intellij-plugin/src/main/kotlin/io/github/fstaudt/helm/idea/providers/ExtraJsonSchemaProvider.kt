package io.github.fstaudt.helm.idea.providers

import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VirtualFile
import io.github.fstaudt.helm.EXTRA_VALUES_SCHEMA_FILE
import java.io.File

class ExtraJsonSchemaProvider(private val jsonSchemasDir: File) : BaseJsonSchemaProvider(jsonSchemasDir) {
    private val jsonSchemaFile = findFileByIoFile(File(jsonSchemasDir, EXTRA_VALUES_SCHEMA_FILE), false)
    override fun isAvailable(file: VirtualFile) = file.name.endsWith("-values.yaml") && super.isAvailable(file)
    override fun getName() = "aggregation for packaged chart ${jsonSchemasDir.name}"
    override fun getSchemaFile() = jsonSchemaFile
}
