package io.github.fstaudt.helm.aggregation.values

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.HelmConstants.HELM_VALUES_FILE
import io.github.fstaudt.helm.Mappers.chartMapper
import io.github.fstaudt.helm.ObjectNodeExtensions.global
import io.github.fstaudt.helm.ObjectNodeExtensions.globalOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.objectNode
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class ExtractedValuesAggregator(private val extractsDir: File) {

    companion object {
        private val TIME_VALUE = Regex(
            """(?m)^(\s*[^#\s][^:\r\n]*:\s*)(\d{1,2}:[0-5]\d:[0-5]\d(?:\.\d+)?)(\s*(?:#.*)?)$"""
        )
        private val yamlMapper = ObjectMapper(YAMLFactory()).also {
            it.registerModule(KotlinModule.Builder().build())
            it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    fun aggregate(chart: Chart): JsonNode {
        return yamlMapper.nodeFactory.objectNode().also {
            chart.dependencies.filter { it.version != null }.forEach { dependency ->
                it.aggregateFor(dependency, extractsDir)
            }
        }
    }

    private fun ObjectNode.aggregateFor(dependency: ChartDependency, extractedChartDir: File) {
        val dependencyDir = File(extractedChartDir, dependency.name)
        val dependencyChart = File(dependencyDir, HELM_CHART_FILE).takeIf { it.exists() }?.let { chartFile ->
            chartFile.inputStream().use { chartMapper.readValue(it, Chart::class.java) }
        }
        val dependencyValues = File(dependencyDir, HELM_VALUES_FILE).takeIf { it.exists() }?.let { valuesFile ->
            yamlMapper.readTree(valuesFile.readText().quoteTimeValues()) as? ObjectNode
        }
        dependencyChart?.dependencies?.forEach {
            objectNode(dependency.aliasOrName()).aggregateFor(it, dependencyDir)
        }
        dependencyValues?.globalOrNull()?.let { global ->
            dependencyChart?.dependencies?.forEach {
                objectNode(dependency.aliasOrName()).propagate(it, dependencyDir, global)
            }
        }
        dependencyValues?.also { objectNode(dependency.aliasOrName()).deepMergeWith(it) }
    }

    private fun String.quoteTimeValues() = TIME_VALUE.replace(this) { match ->
        "${match.groupValues[1]}\"${match.groupValues[2]}\"${match.groupValues[3]}"
    }

    private fun ObjectNode.deepMergeWith(otherNode: ObjectNode): ObjectNode {
        otherNode.fieldNames().forEach { fieldName ->
            val element = get(fieldName)
            val otherElement = otherNode.get(fieldName)
            if (element is ObjectNode && otherElement is ObjectNode) {
                element.deepMergeWith(otherElement)
            } else {
                set<ObjectNode>(fieldName, otherElement)
            }
        }
        return this
    }

    private fun ObjectNode.propagate(dependency: ChartDependency, extractedChartDir: File, globalValues: ObjectNode) {
        val dependencyDir = File(extractedChartDir, dependency.name)
        val dependencyChart = File(dependencyDir, HELM_CHART_FILE).takeIf { it.exists() }?.let { chartFile ->
            chartFile.inputStream().use { chartMapper.readValue(it, Chart::class.java) }
        }
        objectNode(dependency.aliasOrName()).global().deepMergeWith(globalValues)
        dependencyChart?.dependencies?.forEach {
            objectNode(dependency.aliasOrName()).propagate(it, dependencyDir, globalValues)
        }
    }
}
