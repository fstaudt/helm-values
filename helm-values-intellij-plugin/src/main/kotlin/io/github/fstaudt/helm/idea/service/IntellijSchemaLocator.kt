package io.github.fstaudt.helm.idea.service

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.JSON_SCHEMAS_DIR
import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class IntellijSchemaLocator(private val chartDir: File, private val projectBaseDir: File) : SchemaLocator {

    override fun aggregatedSchemaFor(dependency: ChartDependency): File {
        val dependencyChartName = File(chartDir, dependency.localPath()!!).name
        return File(projectBaseDir, "$JSON_SCHEMAS_DIR/$dependencyChartName/$AGGREGATED_SCHEMA_FILE")
    }
}
