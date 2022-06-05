package io.github.fstaudt.helm.tasks

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.unauthorized
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.REPOSITORY_AUTHORIZATION
import io.github.fstaudt.helm.REPOSITORY_PASSWORD
import io.github.fstaudt.helm.REPOSITORY_PORT
import io.github.fstaudt.helm.REPOSITORY_URL
import io.github.fstaudt.helm.REPOSITORY_USER
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.tasks.DownloadJsonSchemas.Companion.DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@WireMockTest(httpPort = REPOSITORY_PORT)
@Suppress("NestedLambdaShadowedImplicitParameter")
class DownloadJsonSchemasTest {
    private lateinit var testProject: TestProject
    private lateinit var downloadDir: File

    companion object {
        private const val UNAVAILABLE_URL = "http://localhost:1981"
        private const val CHARTS = "@charts"
        private const val CHARTS_PATH = "charts"
        private const val PROTECTED = "@protected"
        private const val PROTECTED_PATH = "protected"
        private const val THIRDPARTY_PATH = "thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EXTERNAL_VALUES_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE"
        private const val EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH =
            "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val PROTECTED_SCHEMA = "protected-json-schema"
        private const val PROTECTED_VERSION = "0.3.0"
        private const val PROTECTED_VALUES_SCHEMA_PATH = "$PROTECTED_SCHEMA/$PROTECTED_VERSION/$VALUES_SCHEMA_FILE"
        private const val REF_SCHEMA = "ref-json-schema"
        private const val REF_VERSION = "0.4.0"
        private const val REF_VALUES_SCHEMA_PATH = "$REF_SCHEMA/$REF_VERSION/$VALUES_SCHEMA_FILE"
        private const val REF_GLOBAL_VALUES_SCHEMA_PATH = "$REF_SCHEMA/$REF_VERSION/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val THIRDPARTY_SCHEMA = "thirdparty-json-schema"
        private const val THIRDPARTY_VERSION = "0.5.0"
        private const val THIRDPARTY_VALUES_SCHEMA_PATH = "$THIRDPARTY_SCHEMA/$THIRDPARTY_VERSION/$VALUES_SCHEMA_FILE"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        downloadDir = File("${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS")
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
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
    fun `downloadJsonSchemas should use alias to download JSON schemas of dependencies from JSON schema repository`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EXTERNAL_SCHEMA
                  version: $EXTERNAL_VERSION
                  repository: "$CHARTS"
                  alias: ${EXTERNAL_SCHEMA}-alias
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}-alias/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
            assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}-alias/$GLOBAL_VALUES_SCHEMA_FILE").isFile
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
    fun `downloadJsonSchemas should download JSON schemas and update $ref in downloaded schema when $ref is relative`() {
        stubForSchema("$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE",
            """
            "properties": {
              "ref": {
                "${'$'}ref": "../../$REF_VALUES_SCHEMA_PATH"
              }
            }
            """.trimIndent())
        stubForSchema(REF_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "global": {
                "${'$'}ref": "$GLOBAL_VALUES_SCHEMA_FILE"
                }
              }
            """.trimIndent())
        stubForSchema(REF_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH) },
                    { it.node("properties.ref.\$ref").isEqualTo("$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH") },
                )
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo(REF_VALUES_SCHEMA_PATH) },
                    { it.node("properties.global.\$ref").isEqualTo(GLOBAL_VALUES_SCHEMA_FILE) },
                )
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$CHARTS_PATH/$REF_GLOBAL_VALUES_SCHEMA_PATH").isFile
                .hasContent().node("\$id").isEqualTo(REF_GLOBAL_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas in same folder when $ref is relative in same folder`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "global": {
                "${'$'}ref": "$GLOBAL_VALUES_SCHEMA_FILE"
                }
              }
            """.trimIndent())
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH) },
                    { it.node("properties.global.\$ref").isEqualTo(GLOBAL_VALUES_SCHEMA_FILE) },
                )
            assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas and update $ref in downloaded schema when $ref is relative with fragment`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "../../$REF_VALUES_SCHEMA_PATH#/objects/refs"
              }
            }
            """.trimIndent())
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForSchema(REF_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent()
                .node("properties.refs.\$ref").isEqualTo("$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH#/objects/refs")
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH").isFile
                .hasContent().node("\$id").isEqualTo(REF_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas and update $ref in downloaded schema when $ref is relative and protected`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "../../../$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH#/objects/refs"
              }
            }
            """.trimIndent()
        )
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForProtectedSchema(PROTECTED_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent()
                .node("properties.refs.\$ref")
                .isEqualTo("$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH#/objects/refs")
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH").isFile
                .hasContent().node("\$id").isEqualTo(PROTECTED_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas and update $ref in downloaded schema when $ref is a full URI`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "$REPOSITORY_URL/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH"
              }
            }
            """.trimIndent()
        )
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForThirdPartySchema(THIRDPARTY_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("properties.refs.\$ref").isEqualTo("$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH")

            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH").isFile
                .hasContent().node("\$id").isEqualTo(THIRDPARTY_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas and update $ref in downloaded schema when $ref is a full protected URI`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "$REPOSITORY_URL/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH"
              }
            }
            """.trimIndent()
        )
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForProtectedSchema(PROTECTED_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("properties.refs.\$ref").isEqualTo("$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH")

            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH").isFile
                .hasContent().node("\$id").isEqualTo(PROTECTED_VALUES_SCHEMA_PATH)
        }

    }

    @Test
    fun `downloadJsonSchemas should download JSON schemas and update $ref in downloaded schema when $ref is a full URI with fragment`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "$REPOSITORY_URL/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH#/objects/refs"
              }
            }
            """.trimIndent()
        )
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForThirdPartySchema(THIRDPARTY_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("properties.refs.\$ref")
                .isEqualTo("$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH#/objects/refs")

            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH").isFile
                .hasContent().node("\$id").isEqualTo(THIRDPARTY_VALUES_SCHEMA_PATH)
        }
    }

    @Test
    fun `downloadJsonSchemas should keep $ref with fragment`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "#/objects/refs"
              }
            }
            """.trimIndent()
        )
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().node("properties.refs.\$ref").isEqualTo("#/objects/refs")
        }
    }

    @Test
    fun `downloadJsonSchemas should generate JSON schema with error when JSON schema can't be downloaded from repository`() {
        stubFor(get("/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").willReturn(unauthorized()))
        stubFor(get("/$CHARTS_PATH/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH").willReturn(notFound()))
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also { build ->
            assertThat(build.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val baseUrl = "$REPOSITORY_URL/$CHARTS_PATH"
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Error schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                    { it.node("description").isString.contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                    { it.node("description").isString.contains("401 - Unauthorized") },
                )
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Error schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                    { it.node("description").isString.contains("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                    { it.node("description").isString.contains("404 - Not Found") },
                )
        }
    }

    @Test
    fun `downloadJsonSchemas should generate JSON schema with error when repository is unreachable`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$CHARTS" to JsonSchemaRepository("$UNAVAILABLE_URL/$CHARTS_PATH"),
                  )
                }
            """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, DOWNLOAD_JSON_SCHEMAS).also { build ->
            assertThat(build.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val baseUrl = "$UNAVAILABLE_URL/$CHARTS_PATH"
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Error schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                    { it.node("description").isString.contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                    { it.node("description").isString.contains("HttpHostConnectException - ") },
                )
            assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                    { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("title").isEqualTo("Error schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                    { it.node("description").isString.contains("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                    { it.node("description").isString.contains("HttpHostConnectException - ") },
                )
        }
    }

    @Test
    fun `downloadJsonSchemas should create empty unpack directory when chart has no dependencies`() {
        testProject.initHelmChart()
        testProject.runTask(DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS")).isEmptyDirectory
        }
    }

    @Test
    fun `downloadJsonSchemas should be skipped when there is no chart in Helm sources directory`() {
        File(testProject, "Chart.yaml").delete()
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

    private fun stubForThirdPartySchema(path: String, fileContent: String? = null) {
        val body = Body("""
            {
              "${'$'}schema": "$SCHEMA_VERSION",
              "${'$'}id": "$path"
              ${fileContent?.let { ",$it" } ?: ""}
            }
            """.trimIndent().toByteArray())
        stubFor(get("/$THIRDPARTY_PATH/$path").willReturn(ok().withResponseBody(body)))
    }
}