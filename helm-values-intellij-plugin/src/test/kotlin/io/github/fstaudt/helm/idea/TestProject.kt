package io.github.fstaudt.helm.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.idea.Mappers.metadataMapper
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.CHART_METADATA_FILE
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.JSON_SCHEMAS_DIR
import java.io.File

const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"

fun Project.initHelmChart(
    sourcesDir: File = baseDir(),
    chartName: String = CHART_NAME,
    customizeHelmChart: File.() -> Unit = {}
): File {
    return File(sourcesDir, HELM_CHART_FILE).apply {
        parentFile.mkdirs()
        writeText(
            """
            apiVersion: v2
            name: $chartName
            version: $CHART_VERSION
            description: Test helm chart
            maintainers:
              - name: François Staudt
                url: https://github.com/fstaudt
            icon: https://helm.sh/img/helm.svg
            
        """.trimIndent()
        )
        customizeHelmChart()
    }
}

fun Project.initJsonSchemas(sourcesDir: File = baseDir()): File {
    return File(baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME").also {
        it.mkdirs()
        metadataMapper.writeValue(File(it, CHART_METADATA_FILE), HelmChartMetadata(sourcesDir))
        File(it, AGGREGATED_SCHEMA_FILE).writeText("{}")
        findFileByIoFile(File(it, AGGREGATED_SCHEMA_FILE), true)
    }
}
