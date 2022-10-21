package io.github.fstaudt.helm.idea

import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.HELM_CHARTS_FILE
import java.io.File

const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"

fun Project.initHelmChart(sourcesDir: File = baseDir(), customizeHelmChart: File.() -> Unit = {}): File {
    return File(sourcesDir, HELM_CHARTS_FILE).apply {
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
