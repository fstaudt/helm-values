package io.github.fstaudt.helm.tasks

import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.runAndFail
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.GenerateJsonSchema.Companion.GENERATED
import io.github.fstaudt.helm.tasks.GenerateJsonSchema.Companion.GENERATE_JSON_SCHEMA
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@Suppress("NestedLambdaShadowedImplicitParameter")
class GenerateJsonSchemaTest {
    private lateinit var testProject: TestProject

    companion object {
        const val BASE_URL = "http://charts"
        const val APPS = "@apps"
        const val APPS_PATH = "apps"
        const val BUNDLES = "@bundles"
        const val BUNDLES_PATH = "bundles"
        const val INFRA = "@infra"
        const val INFRA_REPOSITORY_URL = "http://infra.charts"
        const val THIRDPARTY = "@thirdparty"
        const val EXTERNAL_SCHEMA = "external-json-schema"
        const val EMBEDDED_SCHEMA = "embedded-json-schema"
        const val BASE_CHART_URL = "$BASE_URL/$APPS_PATH/$CHART_NAME/$CHART_VERSION"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to RepositoryMapping("$BASE_URL/$APPS_PATH"),
                    "$BUNDLES" to RepositoryMapping("$BASE_URL/$BUNDLES_PATH"),
                    "$INFRA" to RepositoryMapping("$INFRA_REPOSITORY_URL"),
                  )
                  targetRepository = "$APPS"
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
    fun `generateJsonSchema should generate helm-values JSON schema with dependencies in mapped repositories`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: "0.1.0"
                  repository: "$APPS"
                - name: "$EMBEDDED_SCHEMA"
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE") },
                    { it.node("title").isEqualTo("Configuration for chart $APPS/$CHART_NAME/$CHART_VERSION") },
                    { it.node("description").isEqualTo("\\\\n") },
                    { it.node("properties").isObject.containsKey(EXTERNAL_SCHEMA) },
                    { it.node("properties").isObject.doesNotContainKey(EMBEDDED_SCHEMA) },
                    { it.node("properties.global.\$ref").isEqualTo(GLOBAL_VALUES_SCHEMA_FILE) },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use alias as property names in helm-values JSON schema`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("properties").isObject.doesNotContainKey(EXTERNAL_SCHEMA) },
                    {
                        it.node("properties.${EXTERNAL_SCHEMA}-alias.\$ref")
                            .isEqualTo("../../$EXTERNAL_SCHEMA/0.1.0/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use relative ref to external JSON schemas in helm-values when repositories are same`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: "0.1.0"
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                            .isEqualTo("../../$EXTERNAL_SCHEMA/0.1.0/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use relative ref to external JSON schemas in helm-values when repositories have same host`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: "0.1.0"
                  repository: "$BUNDLES"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                            .isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/0.1.0/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use full ref to external JSON schemas in helm-values when repositories are different`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: "0.1.0"
                  repository: "$INFRA"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                            .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/0.1.0/$VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should set property for dependency condition in helm-values`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                  condition: "$EXTERNAL_SCHEMA.enabled"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
                    {
                        it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:0.1.0)")
                        it.node("description").isEqualTo("\\\\n")
                        it.node("type").isEqualTo("boolean")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should generate helm-global JSON schema with dependencies in mapped repositories`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                - name: "$EMBEDDED_SCHEMA"
                  version: "0.1.0"
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE") },
                    { it.node("allOf").isArray.hasSize(1) },
                    { it.node("allOf[0].\$ref").isString.contains(EXTERNAL_SCHEMA) },
                )
        }
    }

    @Test
    fun `generateJsonSchema should not use alias to generate ref to external JSON schemas in helm-global JSON schema`() {
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
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("allOf").isArray.hasSize(1) },
                    { it.node("allOf[0].\$ref").isEqualTo("../../$EXTERNAL_SCHEMA/0.1.0/$GLOBAL_VALUES_SCHEMA_FILE") },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use relative ref to external JSON schemas in helm-global when repositories are same`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("allOf").isArray.hasSize(1) },
                    { it.node("allOf[0].\$ref").isEqualTo("../../$EXTERNAL_SCHEMA/0.1.0/$GLOBAL_VALUES_SCHEMA_FILE") },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use relative ref to external JSON schemas in helm-global when repositories have same host`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$BUNDLES"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("allOf").isArray.hasSize(1) },
                    {
                        it.node("allOf[0].\$ref")
                            .isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/0.1.0/$GLOBAL_VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should use full ref to external JSON schemas in helm-global when repositories are different`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$INFRA"
                """.trimIndent()
            )
        }
        testProject.runTask(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    {
                        it.node("allOf[0].\$ref")
                            .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/0.1.0/$GLOBAL_VALUES_SCHEMA_FILE")
                    },
                )
        }
    }

    @Test
    fun `generateJsonSchema should fail when target repository is not found in repository mappings`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to RepositoryMapping("$BASE_URL/$APPS_PATH"),
                  )
                  targetRepository = "unknown"
                }
            """.trimIndent()
            )
        }
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: "$EXTERNAL_SCHEMA"
                  version: "0.1.0"
                  repository: "$APPS"
                """.trimIndent()
            )
        }
        testProject.runAndFail(GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("targetRepository unknown not found in repository mappings.")
        }
    }

    @Test
    fun `generateJsonSchema should retrieve JSON schemas from cache on second run`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: 0.1.0
                  repository: "$INFRA"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE")
        }
        File("${testProject.buildDir}/$GENERATED").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, GENERATE_JSON_SCHEMA).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMA")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
            assertThatJsonFile("${testProject.buildDir}/$GENERATED/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE")
        }
    }
}