package io.github.fstaudt.helm

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.unauthorized
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

private const val REPOSITORY_PORT = 1982

@WireMockTest(httpPort = REPOSITORY_PORT)
internal class JsonSchemaDownloaderTest {

    companion object {
        private const val REPOSITORY_URL = "http://localhost:$REPOSITORY_PORT"
        private const val REPOSITORY_USER = "user"
        private const val REPOSITORY_PASSWORD = "password"
        private const val REPOSITORY_AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="
        private const val UNAVAILABLE = "@unavailable"
        private const val UNAVAILABLE_URL = "http://localhost:2001"
        private const val CHARTS = "@charts"
        private const val CHARTS_PATH = "charts"
        private const val PROTECTED = "@protected"
        private const val PROTECTED_PATH = "protected"
        private const val THIRDPARTY_PATH = "thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EXTERNAL_VALUES_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE"
        private const val RELATIVE_JSON_SCHEMA = "relative.schema.json"
        private const val RELATIVE_JSON_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$RELATIVE_JSON_SCHEMA"
        private const val PROTECTED_SCHEMA = "protected-json-schema"
        private const val PROTECTED_VERSION = "0.3.0"
        private const val PROTECTED_VALUES_SCHEMA_PATH = "$PROTECTED_SCHEMA/$PROTECTED_VERSION/$VALUES_SCHEMA_FILE"
        private const val REF_SCHEMA = "ref-json-schema"
        private const val REF_VERSION = "0.4.0"
        private const val REF_VALUES_SCHEMA_PATH = "$REF_SCHEMA/$REF_VERSION/$VALUES_SCHEMA_FILE"
        private const val SUB_SCHEMA_FILE = "sub-values.schema.json"
        private const val REF_SUB_SCHEMA_PATH = "$REF_SCHEMA/$REF_VERSION/$SUB_SCHEMA_FILE"
        private const val THIRDPARTY_SCHEMA = "thirdparty-json-schema"
        private const val THIRDPARTY_VERSION = "0.5.0"
        private const val THIRDPARTY_VALUES_SCHEMA_PATH = "$THIRDPARTY_SCHEMA/$THIRDPARTY_VERSION/$VALUES_SCHEMA_FILE"
    }

    private lateinit var testProject: TestProject
    private lateinit var downloader: JsonSchemaDownloader
    private lateinit var downloadDir: File

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        val repositoryMappings = mapOf(
            CHARTS to JsonSchemaRepository("$REPOSITORY_URL/$CHARTS_PATH"),
            UNAVAILABLE to JsonSchemaRepository("$UNAVAILABLE_URL/$CHARTS_PATH"),
            PROTECTED to JsonSchemaRepository("$REPOSITORY_URL/$PROTECTED_PATH", REPOSITORY_USER, REPOSITORY_PASSWORD)
        )
        downloader = JsonSchemaDownloader(repositoryMappings, testProject.downloadSchemasDir)
        downloadDir = testProject.downloadSchemasDir
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
    fun `download should download JSON schemas of dependencies from JSON schema repository`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS),
            ChartDependency("unknown-json-schema", "0.1.0", "@unknown")
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should not use alias to download JSON schemas of dependencies from JSON schema repository`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS, "$EXTERNAL_SCHEMA-alias")
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas of dependencies from protected JSON schema repository`() {
        stubForProtectedSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, PROTECTED)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$PROTECTED_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and keep $ref in downloaded schemas when $ref is relative`() {
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
                "${'$'}ref": "$SUB_SCHEMA_FILE"
                }
              }
            """.trimIndent())
        stubForSchema(REF_SUB_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and(
                { it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH) },
                { it.node("properties.ref.\$ref").isEqualTo("../../$REF_VALUES_SCHEMA_PATH") },
            )
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH").isFile
            .hasContent().and(
                { it.node("\$id").isEqualTo(REF_VALUES_SCHEMA_PATH) },
                { it.node("properties.global.\$ref").isEqualTo(SUB_SCHEMA_FILE) },
            )
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$REF_SUB_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(REF_SUB_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas in same folder when $ref is relative in same folder`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "global": {
                "${'$'}ref": "$RELATIVE_JSON_SCHEMA"
                }
              }
            """.trimIndent())
        stubForSchema(RELATIVE_JSON_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and({
                it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
                it.node("properties.global.\$ref").isEqualTo(RELATIVE_JSON_SCHEMA)
            })
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$RELATIVE_JSON_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(RELATIVE_JSON_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and keep $ref in downloaded schema when $ref is relative with fragment`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "../../$REF_VALUES_SCHEMA_PATH#/objects/refs"
              }
            }
            """.trimIndent())
        stubForSchema(REF_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and({
                it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
                it.node("properties.refs.\$ref").isEqualTo("../../$REF_VALUES_SCHEMA_PATH#/objects/refs")
            })
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(REF_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and keep $ref in downloaded schema when $ref is relative and protected`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "../../../$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH#/objects/refs"
              }
            }
            """.trimIndent()
        )
        stubForProtectedSchema(PROTECTED_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and({
                it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
                it.node("properties.refs.\$ref")
                    .isEqualTo("../../../$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH#/objects/refs")
            })
        assertThatJsonFile("$downloadDir/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(PROTECTED_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and update $ref in downloaded schema when $ref is a full URI`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "$REPOSITORY_URL/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH"
              }
            }
            """.trimIndent()
        )
        stubForThirdPartySchema(THIRDPARTY_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and({
                it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
                it.node("properties.refs.\$ref")
                    .isEqualTo("../../../$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH")
            })
        assertThatJsonFile("$downloadDir/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(THIRDPARTY_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and update $ref in downloaded schema when $ref is a full protected URI`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "$REPOSITORY_URL/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH"
              }
            }
            """.trimIndent()
        )
        stubForProtectedSchema(PROTECTED_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and({
                it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
                it.node("properties.refs.\$ref").isEqualTo("../../../$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH")
            })
        assertThatJsonFile("$downloadDir/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(PROTECTED_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and update $ref in downloaded schema when $ref is a full URI with fragment`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "$REPOSITORY_URL/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH#/objects/refs"
              }
            }
            """.trimIndent()
        )
        stubForThirdPartySchema(THIRDPARTY_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and({
                it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
                it.node("properties.refs.\$ref")
                    .isEqualTo("../../../$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH#/objects/refs")
            })
        assertThatJsonFile("$downloadDir/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(THIRDPARTY_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should keep $ref with fragment`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "#/objects/refs"
              }
            }
            """.trimIndent()
        )
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("properties.refs.\$ref").isEqualTo("#/objects/refs")
    }

    @Test
    fun `download should keep schemas already downloaded`() {
        File(downloadDir, "$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").let {
            it.parentFile.mkdirs()
            it.writeText("{}")
        }
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().isObject.isEmpty()
    }

    @Test
    fun `downloadJsonSchemas should ignore invalid $ref and download next $ref`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH,
            """
            "properties": {
              "refs": {
                "${'$'}ref": "../\"invalid/$HELM_SCHEMA_FILE"
              },
              "next": {
                "${'$'}ref": "$RELATIVE_JSON_SCHEMA"
              }
            }
            """.trimIndent()
        )
        stubForSchema(RELATIVE_JSON_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and(
                { it.node("properties.refs.\$ref").isEqualTo("../\\\"invalid/$HELM_SCHEMA_FILE") },
                { it.node("properties.next.\$ref").isEqualTo(RELATIVE_JSON_SCHEMA) },
            )
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$RELATIVE_JSON_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(RELATIVE_JSON_SCHEMA_PATH)
    }

    @Test
    fun `download should generate fallback JSON schema with error when JSON schema can't be downloaded from repository`() {
        stubFor(get("/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").willReturn(unauthorized()))
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)
        ))
        downloader.download(chart)
        val baseUrl = "$REPOSITORY_URL/$CHARTS_PATH"
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and(
                {
                    it.node("\$schema").isEqualTo(SCHEMA_VERSION)
                    it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH")
                    it.node("type").isEqualTo("object")
                    it.node("additionalProperties").isBoolean.isFalse
                    it.node("title").isEqualTo("Fallback schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
                    it.node("description").isString
                        .contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH")
                        .contains("401 - Unauthorized")
                    it.node("x-intellij-html-description").isString
                        .contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH")
                        .contains("401 - Unauthorized")
                },
            )
    }

    @Test
    fun `download should generate fallback JSON schema with error when repository is unreachable`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, UNAVAILABLE)
        ))
        downloader.download(chart)
        val baseUrl = "$UNAVAILABLE_URL/$CHARTS_PATH"
        assertThatJsonFile("$downloadDir/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").isFile
            .hasContent().and(
                {
                    it.node("\$schema").isEqualTo(SCHEMA_VERSION)
                    it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH")
                    it.node("type").isEqualTo("object")
                    it.node("additionalProperties").isBoolean.isFalse
                    it.node("title").isEqualTo("Fallback schema for $UNAVAILABLE/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
                    it.node("description").isString
                        .contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH")
                        .contains("HttpHostConnectException - ")
                    it.node("x-intellij-html-description").isString
                        .contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH")
                        .contains("HttpHostConnectException - ")
                },
            )
    }

    @Test
    fun `download should ignore dependencies without version or repository`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, null, "no-repository"),
            ChartDependency(EXTERNAL_SCHEMA, null, CHARTS, "no-version"),
        ))
        downloader.download(chart)
        assertThat(downloadDir).isEmptyDirectory
    }

    @Test
    fun `download should create empty downloads directory when chart has no dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        downloader.download(chart)
        assertThat(downloadDir).isEmptyDirectory
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
