package io.github.fstaudt.helm.tasks

import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.initHelmResources
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.AggregateJsonSchema.Companion.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.tasks.AggregateJsonSchema.Companion.AGGREGATE_JSON_SCHEMA
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK_JSON_SCHEMAS
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@Suppress("NestedLambdaShadowedImplicitParameter")
class AggregateJsonSchemaTest {
    private lateinit var testProject: TestProject
    private lateinit var aggregatedSchemaFile: File

    companion object {
        private const val REPOSITORY_URL = "http://localhost:1980"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        private const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        aggregatedSchemaFile = File(testProject.buildDir, "$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        testProject.initHelmChart()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `aggregateJsonSchema should depend on downloadJsonSchemas and unpackJsonSchemas`() {
        testProject.runTask(WITH_BUILD_CACHE, AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isIn(SUCCESS, FROM_CACHE)
        }
    }

    @Test
    fun `aggregateJsonSchema should generate JSON schema for assistance in IDE`() {
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$AGGREGATED_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration for chart $CHART_NAME/$CHART_VERSION") },
                    { it.node("description").isEqualTo("\\n\\\\n ") },
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should aggregate downloaded JSON schemas for dependencies in mapped repositories`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                - name: $NO_SCHEMA
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = NO_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("global.allOf[0].\$ref")
                            .isEqualTo("$DOWNLOADS/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE")
                        it.node("global.allOf").isArray.hasSize(1)
                        it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DOWNLOADS/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")
                        it.isObject.doesNotContainKey(NO_SCHEMA)
                    }
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should disable additional properties`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                - name: $NO_SCHEMA
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = NO_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().and(
                    {
                        it.node("additionalProperties").isBoolean.isFalse
                        it.node("properties.global.additionalProperties").isBoolean.isFalse
                    }
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should use alias to aggregate downloaded JSON schemas`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                  alias: $EXTERNAL_SCHEMA-alias
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("global.allOf[0].\$ref")
                            .isEqualTo("$DOWNLOADS/$EXTERNAL_SCHEMA-alias/$GLOBAL_VALUES_SCHEMA_FILE")
                        it.node("$EXTERNAL_SCHEMA-alias.\$ref")
                            .isEqualTo("$DOWNLOADS/$EXTERNAL_SCHEMA-alias/$VALUES_SCHEMA_FILE")
                    }
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should aggregate unpacked JSON schemas`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = EMBEDDED_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo("$UNPACK/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
                    }
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should aggregate unpacked JSON schemas from sub-chart`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SUB_SCHEMA
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = EMBEDDED_SUB_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref")
                            .isEqualTo("$UNPACK/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
                    },
                    { it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA").isObject.doesNotContainKey("properties") },
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should use alias to aggregate unpacked JSON schemas`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                  alias: $EMBEDDED_SCHEMA-alias
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = EMBEDDED_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("$EMBEDDED_SCHEMA-alias.\$ref")
                            .isEqualTo("$UNPACK/$EMBEDDED_SCHEMA-alias/$HELM_SCHEMA_FILE")
                    }
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should give precedence to downloaded JSON schema over unpacked JSON schema`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = EMBEDDED_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo("$DOWNLOADS/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")
                    }
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should set property for dependency condition in values`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                  condition: "$EXTERNAL_SCHEMA.enabled"
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile
                .hasContent().node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
                    {
                        it.node("title")
                            .isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                        it.node("description").isEqualTo("\\n\\\\n ")
                        it.node("type").isEqualTo("boolean")
                    },
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should set property for dependency condition for third-party dependencies`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$THIRDPARTY"
                  condition: "$EXTERNAL_SCHEMA.enabled"
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile
                .hasContent().node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
                    {
                        it.node("title")
                            .isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($THIRDPARTY/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                        it.node("description").isEqualTo("\\n\\\\n ")
                        it.node("type").isEqualTo("boolean")
                    },
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should use alias to document property for dependency condition`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                  alias: $EXTERNAL_SCHEMA-alias
                  condition: "$EXTERNAL_SCHEMA-alias.enabled"
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile
                .hasContent().node("properties.$EXTERNAL_SCHEMA-alias.properties.enabled").and(
                    {
                        it.node("title")
                            .isEqualTo("Enable $EXTERNAL_SCHEMA-alias dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                        it.node("description").isEqualTo("\\n\\\\n ")
                        it.node("type").isEqualTo("boolean")
                    },
                )
        }
    }

    @Test
    fun `aggregateJsonSchema should be skipped when there is no chart in Helm sources directory`() {
        File(testProject, "Chart.yaml").delete()
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }
}