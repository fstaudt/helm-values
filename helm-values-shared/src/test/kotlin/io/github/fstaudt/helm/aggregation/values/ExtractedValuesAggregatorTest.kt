package io.github.fstaudt.helm.aggregation.values

import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.extractsDir
import io.github.fstaudt.helm.initExtractedHelmDependency
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.testProject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExtractedValuesAggregatorTest {
    companion object {
        private const val EMBEDDED_VALUES = "embedded-values"
        private const val EMBEDDED_VERSION = "0.1.0"
        private const val SUB_CHART = "sub"
        private const val THIRD_LEVEL_CHART = "third"
        private const val APPS = "@apps"
    }

    private lateinit var testProject: TestProject
    private lateinit var aggregator: ExtractedValuesAggregator

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        aggregator = ExtractedValuesAggregator(testProject.extractsDir)
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `aggregate should aggregate values of extracted values from dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES, values = "key: value")
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should aggregate values defined at different levels`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            $SUB_CHART:
              key: value
              $THIRD_LEVEL_CHART:
                third-key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            other-key: value
            $THIRD_LEVEL_CHART:
              other-third-key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART")
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.$SUB_CHART.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.other-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.third-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.other-third-key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should aggregate sub-charts without values`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = null)
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
            """.trimIndent()
        )
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART",
            values = """
            key: value
            """.trimIndent())
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should merge maps of values defined at different levels`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            $SUB_CHART:
              map:
                key: value
              $THIRD_LEVEL_CHART:
                map:
                  third-key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            map:
              other-key: value
            $THIRD_LEVEL_CHART:
              map:
                other-third-key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART")
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.$SUB_CHART.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.map.other-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.map.third-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.map.other-third-key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should aggregate values of sub-charts with alias`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS, alias = "alias")
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
              alias: sub-alias
            """.trimIndent(),
            values = """
            sub-alias:
              key: value
              third-alias:
                third-key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
              alias: third-alias
            """.trimIndent(),
            values = """
            other-key: value
            third-alias:
              other-third-key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART")
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("alias.sub-alias.key").isEqualTo("value")
            it.node("alias.sub-alias.other-key").isEqualTo("value")
            it.node("alias.sub-alias.third-alias.third-key").isEqualTo("value")
            it.node("alias.sub-alias.third-alias.other-third-key").isEqualTo("value")
        })
    }
    @Test
    fun `aggregate should skip dependencies without version`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, null, APPS, "no-version")
        ))
        val values = aggregator.aggregate(chart)
        assertThatJson(values).isObject.doesNotContainKeys(EMBEDDED_VALUES, "no-version")
    }

    @Test
    fun `aggregate should skip dependency when dependency archive is missing or invalid`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = null,
            schema = """
                {
                  "$ID": "fallback"
                }
            """.trimIndent(),
            values = null)
        val values = aggregator.aggregate(chart)
        assertThatJson(values).isObject.doesNotContainKeys(EMBEDDED_VALUES)
    }

    @Test
    fun `aggregate should propagate global values downstream`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              other-key: "value"
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART")
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/no-chart", chartDependencies = null)
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.global.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.global").isObject.doesNotContainKey("other-key")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.other-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.global.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.global.other-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART").isObject.doesNotContainKey("no-chart")
        })
    }

    @Test
    fun `aggregate should propagate global values downstream for dependencies with alias`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS, alias = "alias")
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
              alias: sub-alias
            """.trimIndent(),
            values = """
            global:
              key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
              alias: third-alias
            """.trimIndent(),
            values = """
            global:
              other-key: "value"
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART")
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("alias.global.key").isEqualTo("value")
            it.node("alias.sub-alias.global.key").isEqualTo("value")
            it.node("alias.sub-alias.global.other-key").isEqualTo("value")
            it.node("alias.sub-alias.third-alias.global.key").isEqualTo("value")
            it.node("alias.sub-alias.third-alias.global.other-key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should propagate global values downstream in sub-charts without values`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = null)
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART", values = null)
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.global.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.global.key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should propagate map of global values downstream`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              map:
                key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            chartDependencies = """
            dependencies:
            - name: $THIRD_LEVEL_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              map:
                other-key: "value"
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART/$THIRD_LEVEL_CHART")
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.global.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.global.map").isObject.doesNotContainKey("other-key")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.map.other-key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.global.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.$THIRD_LEVEL_CHART.global.map.other-key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should give precedence to value defined at higher level`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            $SUB_CHART:
              map:
                key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            values = """
            map:
              key: "should be ignored"
              other-key: value
            """.trimIndent())
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.$SUB_CHART.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.map.other-key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should give precedence to list of values defined at higher level`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            $SUB_CHART:
              list: ["value"]
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            values = """
            list: ["should", "be", "ignored"]
            """.trimIndent())
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.$SUB_CHART.list").isArray.containsOnly("value")
        })
    }

    @Test
    fun `aggregate should give precedence to global values defined at higher level`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              map:
                key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            values = """
            global:
              map:
                key: "should be ignored"
                other-key: value
            """.trimIndent())
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.global.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.map.other-key").isEqualTo("value")
        })
    }

    @Test
    fun `aggregate should give precedence to list of global values defined at higher level`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            global:
              list: ["value"]
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            values = """
            global:
              list: ["should", "be", "ignored"]
            """.trimIndent())
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node("$EMBEDDED_VALUES.global.list").isArray.containsOnly("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.list").isArray.containsOnly("value")
        })
    }

    @Test
    fun `aggregate should give precedence to global values defined at higher level in sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_VALUES, EMBEDDED_VERSION, APPS)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_VALUES,
            chartDependencies = """
            dependencies:
            - name: $SUB_CHART
              version: $CHART_VERSION
            """.trimIndent(),
            values = """
            $SUB_CHART:
              global:
                map:
                  key: value
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_VALUES/$SUB_CHART",
            values = """
            global:
              map:
                key: "should be ignored"
                other-key: value
            """.trimIndent())
        val values = aggregator.aggregate(chart)
        assertThatJson(values).and({
            it.node(EMBEDDED_VALUES).isObject.doesNotContainKey("global")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.map.key").isEqualTo("value")
            it.node("$EMBEDDED_VALUES.$SUB_CHART.global.map.other-key").isEqualTo("value")
        })
    }
}
