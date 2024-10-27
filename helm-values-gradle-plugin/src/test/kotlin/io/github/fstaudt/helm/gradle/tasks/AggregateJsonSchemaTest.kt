package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_AGGREGATED_SCHEMA_YAML_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_VALUES_SCHEMA_YAML_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.BASE_URI
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.DEFS
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
import io.github.fstaudt.helm.gradle.tasks.ExtractHelmDependencies.Companion.EXTRACT_HELM_DEPENDENCIES
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
    fun `aggregateJsonSchema should depend on downloadJsonSchemas and extractHelmDependencies`() {
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `aggregateJsonSchema should accept repository mapping provided with put`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings.put("$APPS", JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"))
                }
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `aggregateJsonSchema should run without repository mappings`() {
        testProject.initBuildFile()
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(SUCCESS)
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
                }
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node(ID).isEqualTo("$BASE_URI/$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            it.isObject.doesNotContainKey(REF)
        })
    }

    @Test
    fun `aggregateJsonSchema should aggregate JSON schema of current chart when it is available`() {
        File(testProject, HELM_SCHEMA_FILE).writeText(
            """
            {
              "$ID": "$CHART_NAME/$HELM_SCHEMA_FILE"
            }
            """.trimIndent())
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node("allOf[0].$REF").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
            it.node("$DEFS.local.${HELM_SCHEMA_FILE.escaped()}.$ID")
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
              "$ID": "$CHART_NAME/$HELM_SCHEMA_FILE"
            }
            """.trimIndent())
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                }
                """.trimIndent()
            )
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
            it.node("allOf[0].$REF").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
            it.node("$DEFS.local.${HELM_SCHEMA_FILE.escaped()}.$ID")
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
            it.node("$EXTERNAL_SCHEMA.$REF").isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMA_PATH")
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
                it.node("$EMBEDDED_SCHEMA.$REF").isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
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
                }
                """.trimIndent()
            )
        }
        File(testProject, "$EXTERNAL_SCHEMA/build/$HELM_VALUES").let {
            it.mkdirs()
            File(it, AGGREGATED_SCHEMA_FILE).writeText(
                """
                {
                  "$ID": "$EXTERNAL_SCHEMA"
                }
                """.trimIndent())
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        val externalSchemaPath = "#/$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
            it.node("$EXTERNAL_SCHEMA.$REF").isEqualTo(externalSchemaPath)
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
                }
                """.trimIndent()
            )
        }
        File(testProject, "$EXTERNAL_SCHEMA/build/$HELM_VALUES").let {
            it.mkdirs()
            File(it, AGGREGATED_SCHEMA_FILE).writeText(
                """
                {
                  "$ID": "$EXTERNAL_SCHEMA"
                }
                """.trimIndent())
        }
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
        }
        val externalSchemaPath = "#/$DEFS/local/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
        assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().node("properties").and({
            it.node("$EXTERNAL_SCHEMA.$REF").isEqualTo(externalSchemaPath)
        })
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with aggregated JSON schema patch`() {
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
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with aggregated JSON schema patch in sourcesDir`() {
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
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with provided aggregated JSON schema patch`() {
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
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  aggregatedValuesPatchFile.set(File(project.projectDir, "custom.schema.patch.json"))
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
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with aggregated YAML schema patch`() {
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
        File(testProject, PATCH_AGGREGATED_SCHEMA_YAML_FILE).writeText(
            """
            - op: replace
              path: "/title"
              value: "overridden YAML value"
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden YAML value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with aggregated YAML schema patch in sourcesDir`() {
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
                }
                """.trimIndent()
            )
        }
        File(sourcesDir, PATCH_AGGREGATED_SCHEMA_YAML_FILE).writeText(
            """
            - op: replace
              path: "/title"
              value: "overridden YAML value"
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden YAML value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with provided aggregated YAML schema patch`() {
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
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  aggregatedValuesYamlPatchFile.set(File(project.projectDir, "custom.schema.patch.yaml"))
                }
                """.trimIndent()
            )
        }
        File(testProject, "custom.schema.patch.yaml").writeText(
            """
            - op: replace
              path: "/title"
              value: "overridden YAML value"
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden YAML value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should give precedence on aggregated JSON schema patch over aggregated YAML schema patch`() {
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
              { "op": "replace", "path": "/title", "value": "overridden JSON value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional JSON value" }
            ]
            """.trimIndent()
        )
        File(testProject, PATCH_AGGREGATED_SCHEMA_YAML_FILE).writeText(
            """
            - op: replace
              path: "/title"
              value: "overridden YAML value"
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("title").isEqualTo("overridden JSON value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional JSON value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with values JSON schema patch`() {
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
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with values JSON schema patch in sourcesDir`() {
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
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with provided values JSON schema patch`() {
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
                }
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  valuesPatchFile.set(File(project.projectDir, "custom.schema.patch.json"))
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
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with values YAML schema patch`() {
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
        File(testProject, PATCH_VALUES_SCHEMA_YAML_FILE).writeText(
            """
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with values YAML schema patch in sourcesDir`() {
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
                }
                """.trimIndent()
            )
        }
        File(sourcesDir, PATCH_VALUES_SCHEMA_YAML_FILE).writeText(
            """
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should update aggregated values schema with provided values YAML schema patch`() {
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
                }
                tasks.named<${AggregateJsonSchema::class.java.name}>("$AGGREGATE_JSON_SCHEMA") {
                  valuesYamlPatchFile.set(File(project.projectDir, "custom.schema.patch.yaml"))
                }
                """.trimIndent()
            )
        }
        File(testProject, "custom.schema.patch.yaml").writeText(
            """
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional YAML value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
            })
        }
    }

    @Test
    fun `aggregateJsonSchema should give precedence on values JSON schema patch over values YAML schema patch`() {
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
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional JSON value" }
            ]
            """.trimIndent()
        )
        File(testProject, PATCH_VALUES_SCHEMA_YAML_FILE).writeText(
            """
            - op: add
              path: "/properties/$EXTERNAL_SCHEMA/title"
              value: "additional YAML value"
            """.trimIndent()
        )
        testProject.runTask(AGGREGATE_JSON_SCHEMA).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile(aggregatedSchemaFile).isFile.hasContent().and({
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional JSON value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey(REF)
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
