package io.github.fstaudt.helm.gradle.tasks

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.fstaudt.helm.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.WITH_BUILD_CACHE
import io.github.fstaudt.helm.gradle.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.gradle.buildDir
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.runTask
import io.github.fstaudt.helm.gradle.tasks.DownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.gradle.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

private const val REPOSITORY_PORT = 1980

@WireMockTest(httpPort = REPOSITORY_PORT)
class DownloadJsonSchemasTest {
    private lateinit var testProject: TestProject
    private lateinit var downloadDir: File

    companion object {
        private const val REPOSITORY_URL = "http://localhost:$REPOSITORY_PORT"
        private const val REPOSITORY_USER = "user"
        private const val REPOSITORY_PASSWORD = "password"
        private const val REPOSITORY_AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="
        private const val CHARTS = "@charts"
        private const val CHARTS_PATH = "charts"
        private const val PROTECTED = "@protected"
        private const val PROTECTED_PATH = "protected"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EXTERNAL_VALUES_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE"
        private const val EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        downloadDir = File("${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS")
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$CHARTS" to JsonSchemaRepository("$REPOSITORY_URL/$CHARTS_PATH"),
                    "$PROTECTED" to JsonSchemaRepository("$REPOSITORY_URL/$PROTECTED_PATH", "$REPOSITORY_USER", "$REPOSITORY_PASSWORD")
                  )
                }
                """.trimIndent()
            )
        }
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$CHARTS"
                """.trimIndent()
            )
        }
    }

    @BeforeEach
    fun `init wiremock`() {
        WireMock.reset()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas of dependencies from JSON schema repository`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$CHARTS"
                - name: unknown-json-schema
                  version: 0.1.0
                  repository: "@unknown"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should get chart configuration in sourcesDir`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "sources"
                  repositoryMappings = mapOf(
                    "$CHARTS" to JsonSchemaRepository("$REPOSITORY_URL/$CHARTS_PATH"),
                    "$PROTECTED" to JsonSchemaRepository("$REPOSITORY_URL/$PROTECTED_PATH", "$REPOSITORY_USER", "$REPOSITORY_PASSWORD")
                  )
                }
                """.trimIndent()
            )
        }
        val sourcesDir = File(testProject, "sources").also { it.mkdirs() }
        testProject.clearHelmChart()
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$CHARTS"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas of dependencies from protected JSON schema repository`() {
        stubForProtectedSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        stubForProtectedSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$PROTECTED"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }

    private fun stubForSchema(path: String, fileContent: String? = null) {
        val body = Body("""
            {
              "${'$'}schema": "$SCHEMA_VERSION",
              "${'$'}id": "$path"
              ${fileContent?.let { ",$it" } ?: ""}
            }
            """.trimIndent().toByteArray())
        stubFor(get("/$CHARTS_PATH/$path").willReturn(ok().withResponseBody(body)))
    }

    private fun stubForProtectedSchema(path: String, fileContent: String? = null) {
        val body = Body("""
            {
              "${'$'}schema": "$SCHEMA_VERSION",
              "${'$'}id": "$path"
              ${fileContent?.let { ",$it" } ?: ""}
            }
            """.trimIndent().toByteArray())
        stubFor(get("/$PROTECTED_PATH/$path").withHeader("Authorization", equalTo(REPOSITORY_AUTHORIZATION))
            .willReturn(ok().withResponseBody(body)))
    }
}