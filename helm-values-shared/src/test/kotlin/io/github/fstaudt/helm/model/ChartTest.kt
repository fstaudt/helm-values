package io.github.fstaudt.helm.model

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChartTest {
    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Test
    fun `model should load chart with minimal information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
        """.trimIndent()
        val chart = yamlMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).isNotNull.isEmpty()
    }

    @Test
    fun `model should load chart with unmapped information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
            description: Example helm chart
        """.trimIndent()
        val chart = yamlMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).isNotNull.isEmpty()
    }

    @Test
    fun `model should load chart with dependencies with minimal information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
            dependencies:
            - name: sub-chart
        """.trimIndent()
        val chart = yamlMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).containsExactly(ChartDependency("sub-chart"))
    }

    @Test
    fun `model should load chart with dependencies with all information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
            dependencies:
            - name: sub-chart
              version: 0.1.0
              repository: "@charts"
              alias: alias
              condition: alias.enabled
        """.trimIndent()
        val chart = yamlMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).containsExactly(
            ChartDependency("sub-chart", "0.1.0", "@charts", "alias", "alias.enabled")
        )
    }
}
