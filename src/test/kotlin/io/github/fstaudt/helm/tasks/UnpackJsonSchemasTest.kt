package io.github.fstaudt.helm.tasks

import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.initHelmResources
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.UNPACK_JSON_SCHEMAS
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class UnpackJsonSchemasTest {
    private lateinit var testProject: TestProject

    companion object {
        const val THIRDPARTY_ID = "@thirdparty"
        const val EMBEDDED_SCHEMA = "embedded-json-schema"
        const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        const val INVALID_ARCHIVE = "invalid-archive"
        const val MISSING_ARCHIVE = "missing-archive"
        const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initHelmResources()
        testProject.initBuildFile()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `unpackJsonSchema should unpack JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchema should retrieve JSON schemas from cache on second run`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
        File("${testProject.buildDir}/$UNPACK").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchema should use alias to unpack JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                  alias: ${EMBEDDED_SCHEMA}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/${EMBEDDED_SCHEMA}-alias/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchema should unpack sub-charts JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SUB_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/${EMBEDDED_SUB_SCHEMA}/${EMBEDDED_SCHEMA}/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `unpackJsonSchema should generate JSON schema with error when archive is not found for dependency`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $MISSING_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/$MISSING_ARCHIVE/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$MISSING_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("\$error").isEqualTo("Archive not found") },
                )
        }
    }

    @Test
    fun `unpackJsonSchema should use alias to generate JSON schema with error when archive is not found for dependency`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $MISSING_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                  alias: ${MISSING_ARCHIVE}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/${MISSING_ARCHIVE}-alias/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$MISSING_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("\$error").isEqualTo("Archive not found") },
                )
        }
    }

    @Test
    fun `unpackJsonSchema should generate JSON schema with error when archive is invalid`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $INVALID_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/$INVALID_ARCHIVE/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$INVALID_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("\$error").isString().startsWith("IOException - ") },
                )
        }
    }

    @Test
    fun `unpackJsonSchema should use alias to generate JSON schema with error when archive is invalid`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $INVALID_ARCHIVE
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                  alias: ${INVALID_ARCHIVE}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("${testProject.buildDir}/$UNPACK/${INVALID_ARCHIVE}-alias/values.schema.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$INVALID_ARCHIVE/0.1.0/values.schema.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("\$error").isString().startsWith("IOException - ") },
                )
        }
    }

    @Test
    fun `unpackJsonSchema should create empty unpack directory when dependencies contain no JSON schema`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $NO_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY_ID"
                """.trimIndent()
            )
        }
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$UNPACK")).isEmptyDirectory
        }
    }

    @Test
    fun `unpackJsonSchema should create empty unpack directory when chart has no dependencies`() {
        testProject.initHelmChart()
        testProject.runTask(UNPACK_JSON_SCHEMAS).also {
            assertThat(it.task(":$UNPACK_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$UNPACK")).isEmptyDirectory
        }
    }
}