package io.github.fstaudt.helm.idea

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.EXTRA_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.CHART_METADATA_FILE
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.JSON_SCHEMAS_DIR
import java.io.File

const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"

fun Project.initHelmChart(sourcesDir: File = baseDir(), customizeHelmChart: File.() -> Unit = {}): File {
    return File(sourcesDir, HELM_CHART_FILE).apply {
        parentFile.mkdirs()
        writeText(
            """
            apiVersion: v2
            name: $CHART_NAME
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
    val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    return File(baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME").also {
        it.mkdirs()
        yamlMapper.writeValue(File(it, CHART_METADATA_FILE), HelmChartMetadata(sourcesDir))
        File(it, AGGREGATED_SCHEMA_FILE).writeText("{}")
        File(it, EXTRA_VALUES_SCHEMA_FILE).writeText("{}")
    }
}
