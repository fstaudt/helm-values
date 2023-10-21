package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.BASE_URI
import io.github.fstaudt.helm.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.aggregation.schema.DownloadedSchemaAggregator.Companion.DOWNLOADS
import io.github.fstaudt.helm.aggregation.schema.ExtractedSchemaAggregator.Companion.EXTRACTS
import io.github.fstaudt.helm.aggregation.schema.LocalSchemaAggregator.Companion.LOCAL
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
import io.github.fstaudt.helm.test.assertions.escaped
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
        private const val DEFS = "#/\$defs"
        private const val REPOSITORY_URL = "http://localhost:1980"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EXTERNAL_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        aggregatedSchemaFile = File(testProject.buildDir, "$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")
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
            it.node("\$id").isEqualTo("$BASE_URI/$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            it.isObject.doesNotContainKey("\$ref")
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate JSON schema of current chart when it is available`() {
        File(testProject, HELM_SCHEMA_FILE).writeText(
            """
            {
              "${'$'}id": "$CHART_NAME/$HELM_SCHEMA_FILE"
            }
            """.trimIndent())
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node("allOf[0].\$ref").isEqualTo("$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
            it.node("\$defs.local.${HELM_SCHEMA_FILE.escaped()}.\$id")
                .isEqualTo("$CHART_NAME/$HELM_SCHEMA_FILE")
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate JSON schema of current chart when it is available in sourcesDir`() {
        testProject.clearHelmChart()
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir)
        File(sourcesDir, HELM_SCHEMA_FILE).writeText(
            """
            {
              "${'$'}id": "$CHART_NAME/$HELM_SCHEMA_FILE"
            }
            """.trimIndent())
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
            it.node("allOf[0].\$ref").isEqualTo("$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
            it.node("\$defs.local.${HELM_SCHEMA_FILE.escaped()}.\$id")
                .isEqualTo("$CHART_NAME/$HELM_SCHEMA_FILE")
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
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMA_PATH")
            it.node(NO_SCHEMA).isObject.isEmpty()
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
                it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo("$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should include aggregated JSON schema of dependency when dependency is stored locally`() {
        testProject.clearHelmChart()
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://../$EXTERNAL_SCHEMA"
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
        File(testProject, "$EXTERNAL_SCHEMA/build/$HELM_VALUES").let {
            it.mkdirs()
            File(it, AGGREGATED_SCHEMA_FILE).writeText(
                """
                {
                  "${'$'}id": "$EXTERNAL_SCHEMA"
                }
                """.trimIndent())
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        val externalSchemaPath = "$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(externalSchemaPath)
        })
    }

    @Test
    fun `aggregateJsonSchema should include aggregated JSON schema of dependency when local path ends with slash`() {
        testProject.clearHelmChart()
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "file://../$EXTERNAL_SCHEMA/"
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
        File(testProject, "$EXTERNAL_SCHEMA/build/$HELM_VALUES").let {
            it.mkdirs()
            File(it, AGGREGATED_SCHEMA_FILE).writeText(
                """
                {
                  "${'$'}id": "$EXTERNAL_SCHEMA"
                }
                """.trimIndent())
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        val externalSchemaPath = "$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(externalSchemaPath)
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
    fun `aggregateJsonSchema should retrieve JSON schemas from cache on second run`() {
        testProject.runTask(WITH_BUILD_CACHE, AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isIn(SUCCESS, FROM_CACHE)
        }
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
