package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.WITH_BUILD_CACHE
import io.github.fstaudt.helm.gradle.buildDir
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.initHelmResources
import io.github.fstaudt.helm.gradle.runTask
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

class ExtractJsonSchemasTest {
    private lateinit var testProject: TestProject
    private lateinit var extractSchemasDir: File

    companion object {
        private const val THIRDPARTY = "@thirdparty"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val CHARTS = "@charts"
        private const val CHARTS_URL = "http://localhost/charts"

    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        extractSchemasDir = File(testProject.buildDir, "$HELM_VALUES/$EXTRACT_DIR")
        testProject.initHelmResources()
        testProject.initBuildFile()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `extractJsonSchemas should extract JSON schemas from dependency archives`() {
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
        testProject.runTask(EXTRACT_JSON_SCHEMAS).also {
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `extractJsonSchemas should not extract JSON schemas when dependency repository is in repository mappings`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$CHARTS"
                """.trimIndent()
            )
        }
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$CHARTS" to JsonSchemaRepository("$CHARTS_URL")
                  )
                }
                """.trimIndent()
            )
        }
        testProject.runTask(EXTRACT_JSON_SCHEMAS).also {
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(extractSchemasDir).isEmptyDirectory
        }
    }

    @Test
    fun `extractJsonSchemas should get chart dependencies in sourcesDir`() {
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
        testProject.runTask(EXTRACT_JSON_SCHEMAS).also {
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `extractJsonSchemas should retrieve JSON schemas from cache on second run`() {
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
        testProject.runTask(WITH_BUILD_CACHE, EXTRACT_JSON_SCHEMAS).also {
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
        File("$extractSchemasDir").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, EXTRACT_JSON_SCHEMAS).also {
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/values.schema.json").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/values.schema.json")
        }
    }

    @Test
    fun `extractJsonSchemas should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(EXTRACT_JSON_SCHEMAS).also {
            assertThat(it.task(":$EXTRACT_JSON_SCHEMAS")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }
}
