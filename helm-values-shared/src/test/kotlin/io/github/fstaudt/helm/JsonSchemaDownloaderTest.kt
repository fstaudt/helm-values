package io.github.fstaudt.helm

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
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
        private const val EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val RELATIVE_JSON_SCHEMA = "relative.schema.json"
        private const val RELATIVE_JSON_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$RELATIVE_JSON_SCHEMA"
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
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS),
            ChartDependency("unknown-json-schema", "0.1.0", "@unknown")
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should use alias to download JSON schemas of dependencies from JSON schema repository`() {
        stubForSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS, "$EXTERNAL_SCHEMA-alias")
        ))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}-alias/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
        assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}-alias/$GLOBAL_VALUES_SCHEMA_FILE").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas of dependencies from protected JSON schema repository`() {
        stubForProtectedSchema(EXTERNAL_VALUES_SCHEMA_PATH)
        stubForProtectedSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, PROTECTED)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
            .hasContent().node("\$id").isEqualTo(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and update $ref in downloaded schema when $ref is relative`() {
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
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
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
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}/$VALUES_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$id").isEqualTo(EXTERNAL_VALUES_SCHEMA_PATH) },
                { it.node("properties.global.\$ref").isEqualTo(RELATIVE_JSON_SCHEMA) },
            )
        assertThatJsonFile("$downloadDir/${EXTERNAL_SCHEMA}/$RELATIVE_JSON_SCHEMA").isFile
            .hasContent().node("\$id").isEqualTo(RELATIVE_JSON_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and update $ref in downloaded schema when $ref is relative with fragment`() {
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
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent()
            .node("properties.refs.\$ref").isEqualTo("$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH#/objects/refs")
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$CHARTS_PATH/$REF_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(REF_VALUES_SCHEMA_PATH)
    }

    @Test
    fun `download should download JSON schemas and update $ref in downloaded schema when $ref is relative and protected`() {
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
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent()
            .node("properties.refs.\$ref")
            .isEqualTo("$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH#/objects/refs")
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH").isFile
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
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForThirdPartySchema(THIRDPARTY_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("properties.refs.\$ref").isEqualTo("$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH")

        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH").isFile
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
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForProtectedSchema(PROTECTED_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("properties.refs.\$ref").isEqualTo("$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH")

        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$PROTECTED_PATH/$PROTECTED_VALUES_SCHEMA_PATH").isFile
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
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForThirdPartySchema(THIRDPARTY_VALUES_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("properties.refs.\$ref")
            .isEqualTo("$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH#/objects/refs")
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$THIRDPARTY_PATH/$THIRDPARTY_VALUES_SCHEMA_PATH").isFile
            .hasContent().node("\$id").isEqualTo(THIRDPARTY_VALUES_SCHEMA_PATH)
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
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().node("properties.refs.\$ref").isEqualTo("#/objects/refs")
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
        stubForSchema(EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH)
        stubForSchema(RELATIVE_JSON_SCHEMA_PATH)
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("properties.refs.\$ref").isEqualTo("../\\\"invalid/$HELM_SCHEMA_FILE") },
                { it.node("properties.next.\$ref").isEqualTo(RELATIVE_JSON_SCHEMA) },
            )
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$RELATIVE_JSON_SCHEMA").isFile
            .hasContent().node("\$id").isEqualTo(RELATIVE_JSON_SCHEMA_PATH)
    }

    @Test
    fun `download should generate JSON schema with error when JSON schema can't be downloaded from repository`() {
        stubFor(get("/$CHARTS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH").willReturn(WireMock.unauthorized()))
        stubFor(get("/$CHARTS_PATH/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH").willReturn(WireMock.notFound()))
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, CHARTS)))
        downloader.download(chart)
        val baseUrl = "$REPOSITORY_URL/$CHARTS_PATH"
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                { it.node("description").isString.contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                { it.node("description").isString.contains("401 - Unauthorized") },
            )
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $CHARTS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                { it.node("description").isString.contains("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                { it.node("description").isString.contains("404 - Not Found") },
            )
    }

    @Test
    fun `download should generate JSON schema with error when repository is unreachable`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, UNAVAILABLE)))
        downloader.download(chart)
        val baseUrl = "$UNAVAILABLE_URL/$CHARTS_PATH"
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $UNAVAILABLE/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                { it.node("description").isString.contains("$baseUrl/$EXTERNAL_VALUES_SCHEMA_PATH") },
                { it.node("description").isString.contains("HttpHostConnectException - ") },
            )
        assertThatJsonFile("$downloadDir/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE").isFile
            .hasContent().and(
                { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
                { it.node("\$id").isEqualTo("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                { it.node("type").isEqualTo("object") },
                { it.node("title").isEqualTo("Fallback schema for $UNAVAILABLE/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION") },
                { it.node("description").isString.contains("$baseUrl/$EXTERNAL_GLOBAL_VALUES_SCHEMA_PATH") },
                { it.node("description").isString.contains("HttpHostConnectException - ") },
            )
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
        stubFor(get("/$PROTECTED_PATH/$path").withHeader("Authorization", WireMock.equalTo(REPOSITORY_AUTHORIZATION))
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
