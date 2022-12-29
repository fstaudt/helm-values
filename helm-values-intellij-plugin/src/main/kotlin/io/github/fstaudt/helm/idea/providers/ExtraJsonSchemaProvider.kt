package io.github.fstaudt.helm.idea.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion.SCHEMA_7
import io.github.fstaudt.helm.EXTRA_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.JSON_SCHEMAS_DIR
import java.io.File

class ExtraJsonSchemaProvider(project: Project, private val chartDir: File) : JsonSchemaFileProvider {
    private val jsonSchemaFile = File(project.baseDir(), "$JSON_SCHEMAS_DIR/${chartDir.name}/$EXTRA_VALUES_SCHEMA_FILE")
    override fun isAvailable(file: VirtualFile): Boolean {
        return file.name.endsWith("-values.yaml") && (file.parent?.name == chartDir.name)
    }

    override fun getName() = "aggregation for packaged chart ${chartDir.name}"
    override fun getSchemaFile() = findFileByIoFile(jsonSchemaFile, false)
    override fun getSchemaVersion() = SCHEMA_7
    override fun getSchemaType() = SchemaType.userSchema
}
