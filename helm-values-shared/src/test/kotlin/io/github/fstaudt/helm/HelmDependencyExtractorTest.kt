package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.HelmConstants.HELM_VALUES_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.GENERATOR_LABEL
import io.github.fstaudt.helm.JsonSchemaConstants.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.SCHEMA
import io.github.fstaudt.helm.JsonSchemaConstants.SCHEMA_VERSION
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class HelmDependencyExtractorTest {
    private lateinit var testProject: TestProject
    private lateinit var extractsDir: File
    private lateinit var extractor: HelmDependencyExtractor

    companion object {
        private const val THIRDPARTY = "@thirdparty"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        private const val MULTIPLE_EMBEDDED_SCHEMA = "multiple-embedded-json-schema"
        private const val INVALID_ARCHIVE = "invalid-archive"
        private const val MISSING_ARCHIVE = "missing-archive"
        private const val NO_SCHEMA = "no-json-schema"
        private const val NO_VALUES = "no-values"
        private const val SUBCHART_VERSION = "0.2.0"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        extractsDir = testProject.extractsDir
        extractor = HelmDependencyExtractor(testProject.chartsDir, extractsDir)
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `extract should extract metadata, values and JSON schema from dependency archives`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(EMBEDDED_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $EMBEDDED_SCHEMA")
        assertThat(File("$extractsDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
            .hasContent("key: $EMBEDDED_SCHEMA")
        assertThatJsonFile("$extractsDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION")
            })
    }

    @Test
    fun `extract should extract metadata, values and JSON schema from dependency archive when dependency is provided with version range`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, "^0.0.0", THIRDPARTY)
        ))
        testProject.initHelmResources(EMBEDDED_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $EMBEDDED_SCHEMA")
        assertThat(File("$extractsDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
            .hasContent("key: $EMBEDDED_SCHEMA")
        assertThatJsonFile("$extractsDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION")
            })
    }

    @Test
    fun `extract should extract sub-chart metadata, values and JSON schema from dependency archives`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(EMBEDDED_SUB_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$EMBEDDED_SUB_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $EMBEDDED_SUB_SCHEMA")
        assertThat(File("$extractsDir/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $EMBEDDED_SCHEMA")
        assertThat(File("$extractsDir/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
            .hasContent("key: $EMBEDDED_SUB_SCHEMA")
        assertThatJsonFile("$extractsDir/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION")
            })
    }

    @Test
    fun `extract should extract charts & sub-charts metadata, values and JSON schemas from dependency archives`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(MULTIPLE_EMBEDDED_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(MULTIPLE_EMBEDDED_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$MULTIPLE_EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $MULTIPLE_EMBEDDED_SCHEMA")
        assertThat(File("$extractsDir/$MULTIPLE_EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
            .hasContent("""
                $EMBEDDED_SCHEMA:
                  key: $MULTIPLE_EMBEDDED_SCHEMA
            """.trimIndent())
        assertThatJsonFile("$extractsDir/$MULTIPLE_EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$MULTIPLE_EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("title").isEqualTo("$MULTIPLE_EMBEDDED_SCHEMA $SUBCHART_VERSION")
            })
        assertThat(File("$extractsDir/$MULTIPLE_EMBEDDED_SCHEMA/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $EMBEDDED_SCHEMA")
        assertThat(File("$extractsDir/$MULTIPLE_EMBEDDED_SCHEMA/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
            .hasContent("key: $MULTIPLE_EMBEDDED_SCHEMA")
        assertThatJsonFile("$extractsDir/$MULTIPLE_EMBEDDED_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION")
            })
    }

    @Test
    fun `extract should not create values when dependencies contain no values`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(NO_VALUES, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(NO_VALUES, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$NO_VALUES/$HELM_CHART_FILE")).isFile
            .content().contains("name: $NO_VALUES")
        assertThat(File("$extractsDir/$NO_VALUES/$HELM_VALUES_FILE")).doesNotExist()
    }

    @Test
    fun `extract should not extract chart metadata and values when archive is not found for dependency`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(MISSING_ARCHIVE, SUBCHART_VERSION, THIRDPARTY)
        ))
        extractor.extract(chart)
        assertThat(File("$extractsDir/$MISSING_ARCHIVE/$HELM_CHART_FILE")).doesNotExist()
        assertThat(File("$extractsDir/$MISSING_ARCHIVE/$HELM_VALUES_FILE")).doesNotExist()
    }

    @Test
    fun `extract should not extract chart metadata and values when archive has an outdated version`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, "$SUBCHART_VERSION-prerelease+build", THIRDPARTY)
        ))
        testProject.initHelmResources(EMBEDDED_SUB_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$EMBEDDED_SUB_SCHEMA/$HELM_CHART_FILE")).doesNotExist()
        assertThat(File("$extractsDir/$EMBEDDED_SUB_SCHEMA/$HELM_VALUES_FILE")).doesNotExist()
    }

    @Test
    fun `extract should generate fallback JSON schema with error when archive is not found for dependency`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(MISSING_ARCHIVE, SUBCHART_VERSION, THIRDPARTY)
        ))
        extractor.extract(chart)
        assertThatJsonFile("$extractsDir/$MISSING_ARCHIVE/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(SCHEMA).isEqualTo(SCHEMA_VERSION)
                it.node(ID).isEqualTo("$MISSING_ARCHIVE/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("x-generated-by").isEqualTo(GENERATOR_LABEL)
                it.node("x-generated-at").isString.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}(:\\d{2}){1,2}Z")
                it.node("type").isEqualTo("object")
                it.node("additionalProperties").isBoolean.isFalse
                it.node("title").isEqualTo("Fallback schema for $MISSING_ARCHIVE:$SUBCHART_VERSION")
                it.node("description").isString
                    .contains("$MISSING_ARCHIVE-$SUBCHART_VERSION.tgz")
                    .contains("Archive not found")
                it.node("x-intellij-html-description").isString
                    .contains("$MISSING_ARCHIVE-$SUBCHART_VERSION.tgz")
                    .contains("Archive not found")
            })
    }

    @Test
    fun `extract should not extract chart metadata and values when archive is invalid for dependency`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(INVALID_ARCHIVE, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(INVALID_ARCHIVE, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$INVALID_ARCHIVE/$HELM_CHART_FILE")).doesNotExist()
        assertThat(File("$extractsDir/$INVALID_ARCHIVE/$HELM_VALUES_FILE")).doesNotExist()
    }

    @Test
    fun `extract should generate fallback JSON schema with error when archive is invalid for dependency`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(INVALID_ARCHIVE, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(INVALID_ARCHIVE, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractsDir/$INVALID_ARCHIVE/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(SCHEMA).isEqualTo(SCHEMA_VERSION)
                it.node(ID).isEqualTo("$INVALID_ARCHIVE/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("x-generated-by").isEqualTo(GENERATOR_LABEL)
                it.node("x-generated-at").isString.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}(:\\d{2}){1,2}Z")
                it.node("type").isEqualTo("object")
                it.node("additionalProperties").isBoolean.isFalse
                it.node("title").isEqualTo("Fallback schema for $INVALID_ARCHIVE:$SUBCHART_VERSION")
                it.node("description").isString
                    .contains("$INVALID_ARCHIVE-$SUBCHART_VERSION.tgz")
                    .contains("IOException - ")
                it.node("x-intellij-html-description").isString
                    .contains("$INVALID_ARCHIVE-$SUBCHART_VERSION.tgz")
                    .contains("IOException - ")
            })
    }

    @Test
    fun `extract should not create JSON schema when dependencies contain no JSON schema`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(NO_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(NO_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractsDir/$NO_SCHEMA/$HELM_CHART_FILE").isFile
            .content().contains("name: $NO_SCHEMA")
        assertThatJsonFile("$extractsDir/$NO_SCHEMA/$HELM_VALUES_FILE").isFile
            .hasContent("key: $NO_SCHEMA")
        assertThatJsonFile("$extractsDir/$NO_SCHEMA/$HELM_SCHEMA_FILE").doesNotExist()
    }

    @Test
    fun `extract should extract chart metadata, values and JSON schemas when dependency has no repository`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, SUBCHART_VERSION, null),
        ))
        testProject.initHelmResources(EMBEDDED_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(File("$extractsDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
            .content().contains("name: $EMBEDDED_SCHEMA")
        assertThat(File("$extractsDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
            .hasContent("key: $EMBEDDED_SCHEMA")
        assertThatJsonFile("$extractsDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE")
                it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION")
            })
    }

    @Test
    fun `extract should ignore dependencies without version`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, null, THIRDPARTY, "no-version"),
        ))
        extractor.extract(chart)
        assertThat(extractsDir).isEmptyDirectory
    }

    @Test
    fun `extract should create empty extract directory when chart has no dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        extractor.extract(chart)
        assertThat(extractsDir).isEmptyDirectory
    }
}
