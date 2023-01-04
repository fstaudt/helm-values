package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.EXTRA_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_EXTRA_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.gradle.CHART_NAME
import io.github.fstaudt.helm.gradle.CHART_VERSION
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.WITH_BUILD_CACHE
import io.github.fstaudt.helm.gradle.buildDir
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.initHelmResources
import io.github.fstaudt.helm.gradle.runTask
import io.github.fstaudt.helm.gradle.tasks.AggregateJsonSchema.Companion.AGGREGATE_JSON_SCHEMA
import io.github.fstaudt.helm.gradle.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.tasks.ExtractJsonSchemas.Companion.EXTRACT_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.testProject
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
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
    private lateinit var extraValuesSchemaFile: File

    companion object {
        private const val REPOSITORY_URL = "http://localhost:1980"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EXTERNAL_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE"
        private const val EXTERNAL_GLOBAL_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        aggregatedSchemaFile = File(testProject.buildDir, "$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")
        extraValuesSchemaFile = File(testProject.buildDir, "$HELM_VALUES/$EXTRA_VALUES_SCHEMA_FILE")
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
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
    fun `aggregateJsonSchema should depend on downloadJsonSchemas and extractJsonSchemas`() {
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `aggregateJsonSchema should get chart configuration in sourcesDir`() {
        testProject.clearHelmChart()
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir)
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node("\$id").isEqualTo("$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            it.isObject.doesNotContainKey("\$ref")
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate JSON schema of current chart when it is available`() {
        File(testProject, HELM_SCHEMA_FILE).writeText("{}")
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node("\$ref").isEqualTo("../.././$HELM_SCHEMA_FILE")
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate JSON schema of current chart when it is available in sourcesDir`() {
        testProject.clearHelmChart()
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir)
        File(sourcesDir, HELM_SCHEMA_FILE).writeText("{}")
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node("\$ref").isEqualTo("../../$CHART_NAME/$HELM_SCHEMA_FILE")
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate downloaded JSON schemas for dependencies in mapped repositories`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
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
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[0].\$ref")
                .isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_SCHEMA_PATH#/properties/global")
            it.node("global.allOf[1].\$ref").isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_GLOBAL_SCHEMA_PATH")
            it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_SCHEMA_PATH")
            it.isObject.doesNotContainKey(NO_SCHEMA)
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate extracted JSON schemas`() {
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
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
                it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo("$EXTRACT_DIR/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should aggregate aggregated JSON schema of dependency when dependency is stored locally`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://../$EXTERNAL_SCHEMA"
                """.trimIndent()
            )
        }
        testProject.initHelmResources(chartName = NO_SCHEMA)
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        val externalSchemaPath = "../../../$EXTERNAL_SCHEMA/build/$HELM_VALUES"
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[0].\$ref")
                .isEqualTo("$externalSchemaPath/$AGGREGATED_SCHEMA_FILE#/properties/global")
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$externalSchemaPath/$AGGREGATED_SCHEMA_FILE")
        })
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with aggregated schema patch`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        File(testProject, PATCH_AGGREGATED_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with aggregated schema patch in sourcesDir`() {
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.clearHelmChart()
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
                """.trimIndent()
            )
        }
        File(sourcesDir, PATCH_AGGREGATED_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with provided aggregated schema patch`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  patchAggregatedFile = File(project.projectDir, "custom.schema.patch.json")
                }
                """.trimIndent()
            )
        }
        File(testProject, "custom.schema.patch.json").writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with values schema patch`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        File(testProject, PATCH_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with values schema patch in sourcesDir`() {
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.clearHelmChart()
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
                """.trimIndent()
            )
        }
        File(sourcesDir, PATCH_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with provided values schema patch`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  patchValuesFile = File(project.projectDir, "custom.schema.patch.json")
                }
                """.trimIndent()
            )
        }
        File(testProject, "custom.schema.patch.json").writeText(
            """
            [
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should generate extra values JSON schema`() {
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(extraValuesSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("Extra configuration for packaged chart $CHART_NAME:$CHART_VERSION")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update extra values JSON schema with extra values schema patch`() {
        File(testProject, PATCH_EXTRA_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(extraValuesSchemaFile).isFile.hasContent().node("title").isEqualTo("overridden value")
        }
    }

    @Test
    fun `aggregateJsonSchema should update extra values JSON schema with extra values schema patch in sourcesDir`() {
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.clearHelmChart()
        testProject.initHelmChart(sourcesDir)
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                }
                """.trimIndent()
            )
        }
        File(sourcesDir, PATCH_EXTRA_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(extraValuesSchemaFile).isFile.hasContent().node("title").isEqualTo("overridden value")
        }
    }

    @Test
    fun `aggregateJsonSchema should update extra values JSON schema with provided extra values schema patch`() {
        testProject.initBuildFile {
            appendText(
                """
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  patchExtraValuesFile = File(project.projectDir, "custom.schema.patch.json")
                }
                """.trimIndent()
            )
        }
        File(testProject, "custom.schema.patch.json").writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(extraValuesSchemaFile).isFile.hasContent().node("title").isEqualTo("overridden value")
        }
    }

    @Test
    fun `aggregateJsonSchema should retrieve JSON schemas from cache on second run`() {
        testProject.runTask(WITH_BUILD_CACHE, AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isIn(SUCCESS, FROM_CACHE)
        }
        extraValuesSchemaFile.delete()
        aggregatedSchemaFile.delete()
        testProject.runTask(WITH_BUILD_CACHE, AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(FROM_CACHE)
        }
    }

    @Test
    fun `aggregateJsonSchema should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }
}
