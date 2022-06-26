package io.github.fstaudt.helm.tasks

import io.github.fstaudt.helm.HelmValuesExtension.Companion.HELM_SOURCES_DIR
import io.github.fstaudt.helm.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.clearHelmChart
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.initHelmResources
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.CHARTS_DIR
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
class UnpackJsonSchemasTest {
    private lateinit var testProject: TestProject
    private lateinit var unpackDir: File

    companion object {
        private const val THIRDPARTY = "@thirdparty"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        private const val INVALID_ARCHIVE = "invalid-archive"
        private const val MISSING_ARCHIVE = "missing-archive"
        private const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        unpackDir = File(testProject.buildDir, "$HELM_VALUES/$UNPACK")
        testProject.initHelmResources()
        testProject.initBuildFile()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `unpackJsonSchemas should unpack JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchemas should get chart dependencies in sourcesDir`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "sources"
                }
            """.trimIndent()
            )
        }
        File(testProject, "charts").deleteRecursively()
        testProject.initHelmResources("sources")
        val sourcesDir = File(testProject, "sources").also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchemas should retrieve JSON schemas from cache on second run`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThatJsonFile("$unpackDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
        File("$unpackDir").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("$unpackDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchemas should use alias to unpack JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                  alias: ${EMBEDDED_SCHEMA}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/${EMBEDDED_SCHEMA}-alias/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchemas should unpack sub-charts JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SUB_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/${EMBEDDED_SUB_SCHEMA}/${EMBEDDED_SCHEMA}/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchemas should generate JSON schema with error when archive is not found for dependency`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $MISSING_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/$MISSING_ARCHIVE/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$MISSING_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Fallback schema for $MISSING_ARCHIVE:0.1.0") },
                    { it.node("description").isString.contains("Archive not found") },
                )
        }
    }

    @Test
    fun `unpackJsonSchemas should use alias to generate JSON schema with error when archive is not found for dependency`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $MISSING_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                  alias: ${MISSING_ARCHIVE}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/${MISSING_ARCHIVE}-alias/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$MISSING_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Fallback schema for $MISSING_ARCHIVE:0.1.0") },
                    { it.node("description").isString.contains("Archive not found") },
                )
        }
    }

    @Test
    fun `unpackJsonSchemas should generate JSON schema with error when archive is invalid`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $INVALID_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/$INVALID_ARCHIVE/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$INVALID_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Fallback schema for $INVALID_ARCHIVE:0.1.0") },
                    { it.node("description").isString.contains("IOException - ") },
                )
        }
    }

    @Test
    fun `unpackJsonSchemas should use alias to generate JSON schema with error when archive is invalid`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $INVALID_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                  alias: ${INVALID_ARCHIVE}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$unpackDir/${INVALID_ARCHIVE}-alias/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$INVALID_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Fallback schema for $INVALID_ARCHIVE:0.1.0") },
                    { it.node("description").isString.contains("IOException - ") },
                )
        }
    }

    @Test
    fun `unpackJsonSchemas should create empty unpack directory when dependencies contain no JSON schema`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $NO_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("$unpackDir")).isEmptyDirectory
        }
    }

    @Test
    fun `unpackJsonSchemas should create empty unpack directory when chart has no dependencies`() {
        testProject.initHelmChart()
        File(testProject, "$HELM_SOURCES_DIR/$CHARTS_DIR").deleteRecursively()
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("$unpackDir")).isEmptyDirectory()
        }
    }

    @Test
    fun `downloadJsonSchemas should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }
}