package io.github.fstaudt.helm.model

import com.fasterxml.jackson.databind.JsonMappingException
import io.github.fstaudt.helm.Mappers.chartMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChartTest {
    @Test
    fun `model should load chart with minimal information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
        """.trimIndent()
        val chart = chartMapper.readValue(chartContent, Chart::class.java)
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
        val chart = chartMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).isNotNull.isEmpty()
    }

    @Test
    fun `model should fail to load chart with incomplete information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
        """.trimIndent()
        val error = runCatching { chartMapper.readValue(chartContent, Chart::class.java) }.exceptionOrNull()
        assertThat(error).isNotNull.isInstanceOf(JsonMappingException::class.java)
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
        val chart = chartMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).containsExactly(ChartDependency("sub-chart"))
    }

    @Test
    fun `model should fail to load chart with dependencies with incomplete information`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
            dependencies:
            - version: 0.1.0
              repository: "@charts"
              alias: alias
        """.trimIndent()
        val error = runCatching { chartMapper.readValue(chartContent, Chart::class.java) }.exceptionOrNull()
        assertThat(error).isNotNull.isInstanceOf(JsonMappingException::class.java)
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
              import-values:
              - parent: import-parent
                child: import-child
        """.trimIndent()
        val chart = chartMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).containsExactly(
            ChartDependency("sub-chart", "0.1.0", "@charts", "alias", "alias.enabled",
                listOf(ChartDependencyImport("import-child", "import-parent")))
        )
    }

    @Test
    fun `model should load chart when dependency import is provided as String`() {
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
              import-values:
              - data
        """.trimIndent()
        val chart = chartMapper.readValue(chartContent, Chart::class.java)
        assertThat(chart.apiVersion).isEqualTo("v2")
        assertThat(chart.name).isEqualTo("helm-chart")
        assertThat(chart.version).isEqualTo("0.0.0")
        assertThat(chart.dependencies).containsExactly(
            ChartDependency("sub-chart", "0.1.0", "@charts", "alias", "alias.enabled",
                listOf(ChartDependencyImport("exports.data", "data")))
        )
    }

    @Test
    fun `model should fail to load chart when dependency import is incomplete`() {
        val chartContent = """
            apiVersion: v2
            name: helm-chart
            version: 0.0.0
            dependencies:
            - name: sub-chart
              import-values:
              - parent: import-parent
        """.trimIndent()
        val error = runCatching { chartMapper.readValue(chartContent, Chart::class.java) }.exceptionOrNull()
        assertThat(error).isNotNull.isInstanceOf(JsonMappingException::class.java)
    }
}
