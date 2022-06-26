package io.github.fstaudt.helm.tasks

import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.HelmValuesPlugin.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.HelmValuesPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.HelmValuesPlugin.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.clearHelmChart
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.runAndFail
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATED
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.PATCH_GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.testProject
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
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration for chart $APPS/$CHART_NAME/$CHART_VERSION") },
                    { it.node("description").isEqualTo("\\n\\\\n ") },
                    { it.node("properties").isObject.containsKey(EXTERNAL_SCHEMA) },
                    { it.node("properties").isObject.doesNotContainKey(EMBEDDED_SCHEMA) },
                    { it.node("properties.global.\$ref").isEqualTo(GLOBAL_VALUES_SCHEMA_FILE) },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should get chart configuration in sourcesDir`() {
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
        val sourcesDir = File(testProject, "sources").also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir)
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
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
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo("$BASE_URL/$APPS_PATH/$CHART_NAME/0.2.0/$VALUES_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration for chart $APPS/$CHART_NAME/0.2.0") },
                )
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo("$BASE_URL/$APPS_PATH/$CHART_NAME/0.2.0/$GLOBAL_VALUES_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration of global values for chart $APPS/$CHART_NAME/0.2.0") },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use alias as property names in values JSON schema`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                  alias: ${EXTERNAL_SCHEMA}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("properties").isObject.doesNotContainKey(EXTERNAL_SCHEMA) },
                    {
                        it.node("properties.${EXTERNAL_SCHEMA}-alias.\$ref")
                            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should disable additional properties in values JSON schema`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                  alias: ${EXTERNAL_SCHEMA}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("additionalProperties").isBoolean.isFalse },
                    { it.node("properties.global.additionalProperties").isBoolean.isFalse }
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use relative ref to external JSON schemas in values when repositories are same`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use relative ref to external JSON schemas in values when repositories have same host`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: "$EXTERNAL_VERSION"
                  repository: "$BUNDLES"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                            .isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use full ref to external JSON schemas in values when repositories are different`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                            .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should set property for dependency condition in values`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
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
    fun `generateJsonSchemas should set property for dependency condition for third-party dependencies`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
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
    fun `generateJsonSchemas should use alias to document property for dependency condition`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
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
    fun `generateJsonSchemas should generate global values JSON schema with dependencies in mapped repositories`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                - name: "$EMBEDDED_SCHEMA"
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE") },
                    {
                        it.node("title")
                            .isEqualTo("Configuration of global values for chart $APPS/$CHART_NAME/$CHART_VERSION")
                    },
                    { it.node("description").isEqualTo("\\n\\\\n ") },
                    { it.node("allOf").isArray.hasSize(1) },
                    { it.node("allOf[0].\$ref").isString.contains(EXTERNAL_SCHEMA) },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should not use alias to generate ref to external JSON schemas in global values JSON schema`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$APPS"
                  alias: ${EXTERNAL_SCHEMA}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("allOf").isArray.hasSize(1) },
                    {
                        it.node("allOf[0].\$ref")
                            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use relative ref to external JSON schemas in global values when repositories are same`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("allOf").isArray.hasSize(1) },
                    {
                        it.node("allOf[0].\$ref")
                            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use relative ref to external JSON schemas in global values when repositories have same host`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$BUNDLES"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("allOf").isArray.hasSize(1) },
                    {
                        it.node("allOf[0].\$ref")
                            .isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should use full ref to external JSON schemas in global values when repositories are different`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("allOf[0].\$ref")
                            .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE")
                    },
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
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("title").isEqualTo("overridden value") },
                    { it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value") },
                    { it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref") },
                )
        }
    }

    @Test
    fun `generateJsonSchemas should update generated global values schema with global values schema patch`() {
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
        File(testProject, PATCH_GLOBAL_VALUES_SCHEMA_FILE).writeText(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/allOf/0/title", "value": "additional value" }
            ]
            """.trimIndent()
        )
        testProject.runTask(GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("title").isEqualTo("overridden value") },
                    { it.node("allOf[0].title").isEqualTo("additional value") },
                    { it.node("allOf[0].\$ref").isString.contains(EXTERNAL_SCHEMA) },
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
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE")
        }
        File("${testProject.buildDir}/$GENERATED").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, GENERATE_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE")
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