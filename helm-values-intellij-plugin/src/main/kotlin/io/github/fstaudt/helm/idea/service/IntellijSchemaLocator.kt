package io.github.fstaudt.helm.idea.service

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class IntellijSchemaLocator(private val projectBaseDir: File) : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency): String {
        return "../${dependency.localPath()?.removeSuffix("/")?.substringAfterLast("/")}/$AGGREGATED_SCHEMA_FILE"
    }

    override fun schemaFor(chartDir: File) = "../../../${chartDir.relativePath()}/$HELM_SCHEMA_FILE"

    private fun File.relativePath() = relativeTo(projectBaseDir).takeIf { it.path.isNotEmpty() } ?: File(".")
}
