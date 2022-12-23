package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GENERATION_DIR
import io.github.fstaudt.helm.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.gradle.CHART_NAME
import io.github.fstaudt.helm.gradle.CHART_VERSION
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.WITH_BUILD_CACHE
import io.github.fstaudt.helm.gradle.buildDir
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.runAndFail
import io.github.fstaudt.helm.gradle.runTask
import io.github.fstaudt.helm.gradle.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.testProject
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@Suppress("NestedLambdaShadowedImplicitParameter")
class GenerateJsonSchemasTest {
    private lateinit var testProject: TestProject

    companion object {
        private const val BASE_URL = "http://charts"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val BUNDLES = "@bundles"
        private const val BUNDLES_PATH = "bundles"
        private const val INFRA = "@infra"
        private const val INFRA_REPOSITORY_URL = "http://infra.charts"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_VERSION = "0.3.0"
        private const val BASE_CHART_URL = "$BASE_URL/$APPS_PATH/$CHART_NAME/$CHART_VERSION"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH"),
                    "$BUNDLES" to JsonSchemaRepository("$BASE_URL/$BUNDLES_PATH"),
                    "$INFRA" to JsonSchemaRepository("$INFRA_REPOSITORY_URL"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `generateJsonSchemas should generate values JSON schema with dependencies in mapped repositories`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                - name: "$EMBEDDED_SCHEMA"
                  version: $EMBEDDED_VERSION
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration for chart $APPS/$CHART_NAME:$CHART_VERSION") },
                    { it.node("properties").isObject.containsKey(EXTERNAL_SCHEMA) },
                    { it.node("properties").isObject.doesNotContainKey(EMBEDDED_SCHEMA) },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should get chart configuration in sourcesDir`() {
        val sourcesDir = File(testProject, "sources").also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir)
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "sources"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH")
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `generateJsonSchemas should use publishedVersion from extension when it is defined`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH"),
                    "$BUNDLES" to JsonSchemaRepository("$BASE_URL/$BUNDLES_PATH"),
                    "$INFRA" to JsonSchemaRepository("$INFRA_REPOSITORY_URL"),
                  )
                  publicationRepository = "$APPS"
                  publishedVersion = "0.2.0"
                }
            """.trimIndent()
            )
        }
        testProject.initHelmChart()
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo("$BASE_URL/$APPS_PATH/$CHART_NAME/0.2.0/$VALUES_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration for chart $APPS/$CHART_NAME:0.2.0") },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should update generated values schema with values schema patch`() {
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
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("title").isEqualTo("overridden value") },
                    { it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value") },
                    { it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref") },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should update generated values schema with values schema patch in sourcesDir`() {
        val sourcesDir = File(testProject, "sources").also { it.mkdirs() }
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
                  sourcesDir = "sources"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH")
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        File(sourcesDir, PATCH_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("title").isEqualTo("overridden value") },
                    { it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value") },
                    { it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref") },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should update generated values schema with provided values schema patch`() {
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
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH")
                  )
                  publicationRepository = "$APPS"
                }
                tasks.named<${GenerateJsonSchemas::class.java.name}>("$GENERATE_JSON_SCHEMAS") {
                  patchValuesFile = File(project.projectDir, "custom.schema.patch.json")
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("title").isEqualTo("overridden value") },
                    { it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value") },
                    { it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref") },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should fail when publication repository is not found in repository mappings`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH"),
                  )
                  publicationRepository = "unknown"
                }
            """.trimIndent()
            )
        }
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.runAndFail(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication repository unknown not found in repository mappings.")
        }
    }

    @Test
    fun `generateJsonSchemas should fail when publication repository is not provided`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$BASE_URL/$APPS_PATH"),
                  )
                }
            """.trimIndent()
            )
        }
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.runAndFail(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication repository null not found in repository mappings.")
        }
    }

    @Test
    fun `generateJsonSchemas should retrieve JSON schemas from cache on second run`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$INFRA"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
        }
        File("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$HELM_VALUES/$GENERATION_DIR/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `generateJsonSchemas should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }
}
