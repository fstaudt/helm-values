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
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.AggregateJsonSchema.Companion.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.tasks.AggregateJsonSchema.Companion.AGGREGATE_JSON_SCHEMA
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK_JSON_SCHEMAS
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
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
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
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
                - name: $EMBEDDED_SCHEMA
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).exists()
                .hasContent().node("properties").and(
                    {
                        it.node("global.allOf[0].\$ref")
                            .isEqualTo("$DOWNLOADS/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE")
                        it.node("global.allOf").isArray.hasSize(1)
                        it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DOWNLOADS/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")
                        it.isObject.doesNotContainKey(EMBEDDED_SCHEMA)
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
}