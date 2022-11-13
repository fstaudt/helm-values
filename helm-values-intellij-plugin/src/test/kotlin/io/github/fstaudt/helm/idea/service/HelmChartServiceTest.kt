package io.github.fstaudt.helm.idea.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_CHARTS_FILE
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.PACKAGED_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_PACKAGED_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.CHART_VERSION
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.JSON_SCHEMAS_DIR
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepository
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import org.assertj.core.api.Assertions.assertThat
import java.io.File

private const val REPOSITORY_PORT = 1983

class HelmChartServiceTest : BasePlatformTestCase() {
    companion object {
        private const val OTHER_CHART_NAME = "other-chart"
        private const val EXTERNAL = "@external"
        private const val THIRDPARTY = "@thirdparty"
        private const val REPOSITORY_URL = "http://localhost:$REPOSITORY_PORT"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.1.0"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_VERSION = "0.1.0"
        private const val CHART_DOWNLOADS_DIR = "$JSON_SCHEMAS_DIR/$CHART_NAME/$DOWNLOADS_DIR"
        private const val CHART_EXTRACT_DIR = "$JSON_SCHEMAS_DIR/$CHART_NAME/$EXTRACT_DIR"
    }

    private lateinit var state: HelmValuesSettings
    private lateinit var service: HelmChartService

    private fun reset() {
        state = HelmValuesSettings.instance.state
        state.jsonSchemaRepositories = emptyMap()
        service = HelmChartService.instance
        File(project.baseDir(), JSON_SCHEMAS_DIR).deleteRecursively()
        File(project.baseDir(), PATCH_AGGREGATED_SCHEMA_FILE).delete()
        File(project.baseDir(), PATCH_VALUES_SCHEMA_FILE).delete()
        File(project.baseDir(), PATCH_PACKAGED_SCHEMA_FILE).delete()
    }

    fun `test - aggregate should download JSON schemas from external repositories`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepository(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
            """.trimIndent())
        }
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThat(File(project.baseDir(), "$CHART_DOWNLOADS_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")).isFile
    }

    fun `test - aggregate should extract JSON schemas from Helm dependencies`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepository(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: $EMBEDDED_VERSION
                  repository: "$THIRDPARTY"
            """.trimIndent())
        }
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThat(File(project.baseDir(), "$CHART_EXTRACT_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")).isFile
    }

    fun `test - aggregate should generate aggregated JSON schema`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepository(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
                - name: $EMBEDDED_SCHEMA
                  version: $EMBEDDED_VERSION
                  repository: "$THIRDPARTY"
            """.trimIndent())
        }
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().node("properties").and({
                it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo("$EXTRACT_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")
                it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should update aggregated JSON schema with values schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepository(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
            """.trimIndent())
        }
        File(project.baseDir(), PATCH_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
    }

    fun `test - aggregate should update aggregated JSON schema with aggregated schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepository(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
            """.trimIndent())
        }
        File(project.baseDir(), PATCH_AGGREGATED_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("title").isEqualTo("overridden value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
    }

    fun `test - aggregate should generate packaged JSON schema`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepository(REPOSITORY_URL))
        project.initHelmChart()
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$PACKAGED_SCHEMA_FILE")).isFile
            .hasContent().node("title").isEqualTo("Configuration for packaged chart $CHART_NAME:$CHART_VERSION")
    }

    fun `test - aggregate should update packaged JSON schema with packaged schema patch`() {
        reset()
        project.initHelmChart()
        File(project.baseDir(), PATCH_PACKAGED_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" }
            ]
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$PACKAGED_SCHEMA_FILE")).isFile
            .hasContent().node("title").isEqualTo("overridden value")
    }

    fun `test - clear should delete JSON schemas directory for current chart`() {
        reset()
        project.initHelmChart()
        val chartFolder = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME")
        writeEmptyJsonIn(File(chartFolder, AGGREGATED_SCHEMA_FILE))
        writeEmptyJsonIn(File(chartFolder, "$EXTRACT_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE"))
        writeEmptyJsonIn(File(chartFolder, "$DOWNLOADS_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE"))
        service.clear(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThat(chartFolder).doesNotExist()
    }

    fun `test - clear should succeed when JSON schemas directory is missing`() {
        reset()
        val jsonSchemasDir = File(project.baseDir(), JSON_SCHEMAS_DIR)
        project.initHelmChart()
        service.clear(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThat(jsonSchemasDir).doesNotExist()
    }

    fun `test - clear should keep JSON schemas directories of other charts`() {
        reset()
        project.initHelmChart()
        val chartFolder = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME")
        writeEmptyJsonIn(File(chartFolder, AGGREGATED_SCHEMA_FILE))
        val otherChartFolder = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$OTHER_CHART_NAME")
        val otherAggregatedFile = File(otherChartFolder, AGGREGATED_SCHEMA_FILE).also { writeEmptyJsonIn(it) }
        val otherExtractedFile = File(otherChartFolder, "$EXTRACT_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")
            .also { writeEmptyJsonIn(it) }
        val otherDownloadedFile = File(otherChartFolder, "$DOWNLOADS_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")
            .also { writeEmptyJsonIn(it) }
        service.clear(project, File(project.baseDir(), HELM_CHARTS_FILE))
        assertThat(chartFolder).doesNotExist()
        assertThat(otherChartFolder).isDirectory
        assertThat(otherAggregatedFile).isFile
        assertThat(otherDownloadedFile).isFile
        assertThat(otherExtractedFile).isFile
    }

    private fun writeEmptyJsonIn(file: File) = file.let { it.parentFile.mkdirs(); it.writeText("{}") }
}
