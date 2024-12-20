package io.github.fstaudt.helm.idea.service

import com.intellij.testFramework.HeavyPlatformTestCase
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_AGGREGATED_SCHEMA_YAML_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_VALUES_SCHEMA_YAML_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.BASE_URI
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.DEFS
import io.github.fstaudt.helm.aggregation.schema.DownloadedSchemaAggregator.Companion.DOWNLOADS
import io.github.fstaudt.helm.aggregation.schema.ExtractedSchemaAggregator.Companion.EXTRACTS
import io.github.fstaudt.helm.aggregation.schema.LocalSchemaAggregator.Companion.LOCAL
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.CHART_VERSION
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.Mappers.metadataMapper
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.CHART_METADATA_FILE
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.JSON_SCHEMAS_DIR
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryState
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.test.assertions.escaped
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class HelmJsonSchemaServiceTest : HeavyPlatformTestCase() {
    companion object {
        private const val OTHER_CHART_NAME = "other-chart"
        private const val EXTERNAL = "@external"
        private const val THIRDPARTY = "@thirdparty"
        private const val REPOSITORY_URL = "http://localhost:1983"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.1.0"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_VERSION = "0.1.0"
        private const val CHART_DOWNLOADS_DIR = "$JSON_SCHEMAS_DIR/$CHART_NAME/$DOWNLOADS_DIR"
        private const val CHART_EXTRACT_DIR = "$JSON_SCHEMAS_DIR/$CHART_NAME/$EXTRACTS_DIR"
    }

    private lateinit var state: HelmValuesSettings
    private lateinit var service: HelmJsonSchemaService

    private fun reset() {
        state = HelmValuesSettings.instance().state
        state.reset()
        service = HelmJsonSchemaService.instance()
    }

    fun `test - aggregate should download JSON schemas from external repositories`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
            """.trimIndent())
        }
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThat(File(project.baseDir(),
            "$CHART_DOWNLOADS_DIR/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")).isFile
    }

    fun `test - aggregate should extract JSON schemas from Helm dependencies`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: $EMBEDDED_VERSION
                  repository: "$THIRDPARTY"
            """.trimIndent())
        }
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThat(File(project.baseDir(), "$CHART_EXTRACT_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")).isFile
    }

    fun `test - aggregate should generate aggregated JSON schema`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
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
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.isObject.doesNotContainKey(REF)
                it.node("properties.$EMBEDDED_SCHEMA.$REF")
                    .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")
                it.node("properties.$EXTERNAL_SCHEMA.$REF")
                    .isEqualTo("#/$DEFS/$DOWNLOADS/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should generate metadata file with chart dir`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        val subdir = File(project.baseDir(), CHART_NAME)
        project.initHelmChart(subdir)
        service.aggregate(project, File(subdir, HELM_CHART_FILE))
        val chartMetadataFile = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$CHART_METADATA_FILE")
        assertThat(chartMetadataFile).isFile.exists()
        val chartMetadata = chartMetadataFile.inputStream().use {
            metadataMapper.readValue(it, HelmChartMetadata::class.java)
        }
        assertThat(chartMetadata.dir.path).isEqualTo("$subdir")
    }

    fun `test - aggregate should include JSON schema of current chart when it is available`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart()
        File(project.baseDir(), HELM_SCHEMA_FILE).writeText(
            """
            {
              "$ID": "$CHART_NAME/$HELM_SCHEMA_FILE"
            }
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("allOf[0].$REF").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
                it.node("$DEFS.$LOCAL.${HELM_SCHEMA_FILE.escaped()}.$ID")
                    .isEqualTo("$CHART_NAME/$HELM_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should include JSON schema of current chart when it is available in project subdirectory`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        val subdir = File(project.baseDir(), CHART_NAME)
        project.initHelmChart(subdir)
        File(subdir, HELM_SCHEMA_FILE).writeText(
            """
            {
              "$ID": "$CHART_NAME/$HELM_SCHEMA_FILE"
            }
            """.trimIndent()
        )
        service.aggregate(project, File(subdir, HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("allOf[0].$REF").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
                it.node("$DEFS.$LOCAL.${HELM_SCHEMA_FILE.escaped()}.$ID")
                    .isEqualTo("$CHART_NAME/$HELM_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should include aggregated JSON schema of dependency when dependency is stored locally`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart(File(project.baseDir(), CHART_NAME)) {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://../$EXTERNAL_SCHEMA"
            """.trimIndent())
        }
        project.initHelmChart(File(project.baseDir(), EXTERNAL_SCHEMA), EXTERNAL_SCHEMA)
        service.aggregate(project, File(project.baseDir(), "$CHART_NAME/$HELM_CHART_FILE"))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.$REF")
                    .isEqualTo("#/$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
                it.node("$DEFS.local.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}.$ID")
                    .isEqualTo("$BASE_URI/$EXTERNAL_SCHEMA/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should generate aggregated JSON schema of dependency when dependency is stored locally`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart(File(project.baseDir(), CHART_NAME)) {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://../$EXTERNAL_SCHEMA"
            """.trimIndent())
        }
        project.initHelmChart(File(project.baseDir(), EXTERNAL_SCHEMA), EXTERNAL_SCHEMA)
        service.aggregate(project, File(project.baseDir(), "$CHART_NAME/$HELM_CHART_FILE"))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node(ID).isEqualTo("$BASE_URI/$EXTERNAL_SCHEMA/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should skip invalid local dependencies`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart(File(project.baseDir(), CHART_NAME)) {
            appendText("""
                dependencies:
                - name: invalid
                  version: $EXTERNAL_VERSION
                  repository: "file://../invalid"
            """.trimIndent())
        }
        service.aggregate(project, File(project.baseDir(), "$CHART_NAME/$HELM_CHART_FILE"))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.invalid.$REF").isEqualTo("#/$DEFS/local/invalid/$AGGREGATED_SCHEMA_FILE")
                it.node("$DEFS.local.invalid.${AGGREGATED_SCHEMA_FILE.escaped()}").isObject.isEmpty()
            })
    }

    fun `test - aggregate should include aggregated JSON schema of dependency when dependency is stored in child folder`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://sub-charts/$EXTERNAL_SCHEMA"
            """.trimIndent())
        }
        project.initHelmChart(File(project.baseDir(), "sub-charts/$EXTERNAL_SCHEMA"), EXTERNAL_SCHEMA)
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.$REF")
                    .isEqualTo("#/$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
                it.node("$DEFS.local.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}.$ID")
                    .isEqualTo("$BASE_URI/$EXTERNAL_SCHEMA/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should include aggregated JSON schema of dependency when local path ends with slash`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart(File(project.baseDir(), CHART_NAME)) {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://../$EXTERNAL_SCHEMA/"
            """.trimIndent())
        }
        File(project.baseDir(), "$JSON_SCHEMAS_DIR/$EXTERNAL_SCHEMA").let {
            it.mkdirs()
            File(it, AGGREGATED_SCHEMA_FILE).writeText(
                """
                {
                  "$ID": "$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
                }
                """.trimIndent()
            )
        }
        service.aggregate(project, File(project.baseDir(), "$CHART_NAME/$HELM_CHART_FILE"))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.$REF")
                    .isEqualTo("#/$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
                it.node("$DEFS.local.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}.$ID")
                    .isEqualTo("$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
            })
    }

    fun `test - aggregate should update aggregated JSON schema with values JSON schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
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
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
    }

    fun `test - aggregate should update aggregated JSON schema with values YAML schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
            """.trimIndent())
        }
        File(project.baseDir(), PATCH_VALUES_SCHEMA_YAML_FILE).writeText(
            """
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
    }

    fun `test - aggregate should give precedence on values JSON schema patch over values YAML schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
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
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional JSON value" }
            ]
            """.trimIndent()
        )
        File(project.baseDir(), PATCH_VALUES_SCHEMA_YAML_FILE).writeText(
            """
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional JSON value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
    }

    fun `test - aggregate should update aggregated JSON schema with aggregated JSON schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
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
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("title").isEqualTo("overridden value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
    }

    fun `test - aggregate should update aggregated JSON schema with aggregated YAML schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
        project.initHelmChart {
            appendText("""
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$EXTERNAL"
            """.trimIndent())
        }
        File(project.baseDir(), PATCH_AGGREGATED_SCHEMA_YAML_FILE).writeText(
            """
            - op: replace
              path: "/title"
              "value": "overridden YAML value"
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("title").isEqualTo("overridden YAML value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
    }

    fun `test - aggregate should give precedence on aggregated JSON schema patch over aggregated YAML schema patch`() {
        reset()
        state.jsonSchemaRepositories = mapOf(EXTERNAL to JsonSchemaRepositoryState(REPOSITORY_URL))
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
              { "op": "replace", "path": "/title", "value": "overridden JSON value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional JSON value" }
            ]
            """.trimIndent()
        )
        File(project.baseDir(), PATCH_AGGREGATED_SCHEMA_YAML_FILE).writeText(
            """
            - op: replace
              path: "/title"
              "value": "overridden YAML value"
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        service.aggregate(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThatJsonFile(File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")).isFile
            .hasContent().and({
                it.node("title").isEqualTo("overridden JSON value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional JSON value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
    }

    fun `test - clear should delete JSON schemas directory for current chart`() {
        reset()
        project.initHelmChart()
        val chartFolder = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME")
        writeEmptyJsonIn(File(chartFolder, AGGREGATED_SCHEMA_FILE))
        writeEmptyJsonIn(File(chartFolder, "$EXTRACTS_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE"))
        writeEmptyJsonIn(File(chartFolder, "$DOWNLOADS_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE"))
        service.clear(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThat(chartFolder).doesNotExist()
    }

    fun `test - clear should succeed when JSON schemas directory is missing`() {
        reset()
        val jsonSchemasDir = File(project.baseDir(), JSON_SCHEMAS_DIR)
        project.initHelmChart()
        service.clear(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThat(jsonSchemasDir).doesNotExist()
    }

    fun `test - clear should keep JSON schemas directories of other charts`() {
        reset()
        project.initHelmChart()
        val chartFolder = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$CHART_NAME")
        writeEmptyJsonIn(File(chartFolder, AGGREGATED_SCHEMA_FILE))
        val otherChartFolder = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$OTHER_CHART_NAME")
        val otherAggregatedFile = File(otherChartFolder, AGGREGATED_SCHEMA_FILE).also { writeEmptyJsonIn(it) }
        val otherExtractedFile = File(otherChartFolder, "$EXTRACTS_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")
            .also { writeEmptyJsonIn(it) }
        val otherDownloadedFile = File(otherChartFolder, "$DOWNLOADS_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")
            .also { writeEmptyJsonIn(it) }
        service.clear(project, File(project.baseDir(), HELM_CHART_FILE))
        assertThat(chartFolder).doesNotExist()
        assertThat(otherChartFolder).isDirectory
        assertThat(otherAggregatedFile).isFile
        assertThat(otherDownloadedFile).isFile
        assertThat(otherExtractedFile).isFile
    }

    private fun writeEmptyJsonIn(file: File) = file.let { it.parentFile.mkdirs(); it.writeText("{}") }
}
