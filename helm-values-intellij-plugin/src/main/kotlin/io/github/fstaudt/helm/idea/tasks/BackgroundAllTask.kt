package io.github.fstaudt.helm.idea.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.idea.chartFile
import io.github.fstaudt.helm.idea.helmChartDirs
import io.github.fstaudt.helm.model.Chart
import java.io.File
import kotlin.math.max

abstract class BackgroundAllTask(private val project: Project, key: String) : BackgroundTask(project, key) {
    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private data class OrderedChart(val dir: File, var order: Int = 0)

    protected fun orderedHelmChartDirs(): List<File> {
        return project.helmChartDirs().associate { Pair(it.canonicalPath, OrderedChart(it)) }
            .also { charts ->
                charts.values.forEach { chart ->
                    chart.dir.chartFile().toChart().dependencies.forEach { dependency ->
                        if (dependency.isStoredLocally()) {
                            charts[File(chart.dir, "${dependency.localPath()}").canonicalPath]?.let { dependentChart ->
                                val chartOrder = max(chart.order, dependentChart.order + 1)
                                charts.values.filter { it.order >= chartOrder }.forEach { it.order++ }
                                chart.order = chartOrder
                            }
                        }
                    }
                }
            }
            .map { it.value }
            .sortedBy { it.order }
            .map { it.dir }
    }

    private fun File.toChart() = inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
}
