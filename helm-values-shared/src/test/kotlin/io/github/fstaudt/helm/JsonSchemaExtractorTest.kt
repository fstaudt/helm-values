package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class JsonSchemaExtractorTest {
    private lateinit var testProject: TestProject
    private lateinit var extractSchemasDir: File
    private lateinit var extractor: JsonSchemaExtractor

    companion object {
        private const val CHARTS = "@charts"
        private const val CHARTS_URL = "http://localhost/charts"
        private const val THIRDPARTY = "@thirdparty"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        private const val INVALID_ARCHIVE = "invalid-archive"
        private const val MISSING_ARCHIVE = "missing-archive"
        private const val NO_SCHEMA = "no-json-schema"
        private const val SUBCHART_VERSION = "0.2.0"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        extractSchemasDir = testProject.extractSchemasDir
        val repositoryMappings = mapOf(
            CHARTS to JsonSchemaRepository(CHARTS_URL),
        )
        extractor = JsonSchemaExtractor(File(testProject, "charts"), repositoryMappings, extractSchemasDir)
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `extract should extract JSON schemas from dependency archives`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(EMBEDDED_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$id").isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION") }
            )
    }

    @Test
    fun `extract should use alias to extract JSON schemas from dependency archives`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, SUBCHART_VERSION, THIRDPARTY, "$EMBEDDED_SCHEMA-alias")
        ))
        testProject.initHelmResources(EMBEDDED_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA-alias/$HELM_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$id").isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION") }
            )
    }

    @Test
    fun `extract should extract sub-charts JSON schemas from dependency archives`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(EMBEDDED_SUB_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$id").isEqualTo("$EMBEDDED_SCHEMA/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("title").isEqualTo("$EMBEDDED_SCHEMA $SUBCHART_VERSION") }
            )
    }

    @Test
    fun `extract should generate JSON schema with error when archive is not found for dependency`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(MISSING_ARCHIVE, SUBCHART_VERSION, THIRDPARTY)
        ))
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$MISSING_ARCHIVE/$HELM_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$MISSING_ARCHIVE/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $MISSING_ARCHIVE:$SUBCHART_VERSION") },
                { it.node("description").isString.contains("Archive not found") },
            )
    }

    @Test
    fun `extract should use alias to generate JSON schema with error when archive is not found for dependency`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(MISSING_ARCHIVE, SUBCHART_VERSION, THIRDPARTY, "$MISSING_ARCHIVE-alias")
        ))
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$MISSING_ARCHIVE-alias/$HELM_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$MISSING_ARCHIVE/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $MISSING_ARCHIVE:$SUBCHART_VERSION") },
                { it.node("description").isString.contains("Archive not found") },
            )
    }

    @Test
    fun `extract should generate JSON schema with error when archive is invalid`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(INVALID_ARCHIVE, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(INVALID_ARCHIVE, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$INVALID_ARCHIVE/values.schema.json").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$INVALID_ARCHIVE/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $INVALID_ARCHIVE:$SUBCHART_VERSION") },
                { it.node("description").isString.contains("IOException - ") },
            )
    }

    @Test
    fun `extract should use alias to generate JSON schema with error when archive is invalid`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(INVALID_ARCHIVE, SUBCHART_VERSION, THIRDPARTY, "$INVALID_ARCHIVE-alias")
        ))
        testProject.initHelmResources(INVALID_ARCHIVE, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThatJsonFile("$extractSchemasDir/$INVALID_ARCHIVE-alias/$HELM_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$INVALID_ARCHIVE/$SUBCHART_VERSION/$HELM_SCHEMA_FILE") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $INVALID_ARCHIVE:$SUBCHART_VERSION") },
                { it.node("description").isString.contains("IOException - ") },
            )
    }

    @Test
    fun `extract should create empty extract directory when dependencies contain no JSON schema`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(NO_SCHEMA, SUBCHART_VERSION, THIRDPARTY)
        ))
        testProject.initHelmResources(NO_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(extractSchemasDir).isEmptyDirectory
    }

    @Test
    fun `extract should not extract JSON schemas from dependency when repository is in repository mappings`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(NO_SCHEMA, SUBCHART_VERSION, CHARTS)
        ))
        testProject.initHelmResources(NO_SCHEMA, SUBCHART_VERSION)
        extractor.extract(chart)
        assertThat(extractSchemasDir).isEmptyDirectory
    }

    @Test
    fun `extract should create empty extract directory when chart has no dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        extractor.extract(chart)
        assertThat(extractSchemasDir).isEmptyDirectory
    }
}
