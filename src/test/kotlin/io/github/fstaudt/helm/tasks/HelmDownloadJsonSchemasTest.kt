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
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.assertions.JsonFileAssert.Companion.assertThatJsonFile
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmResources
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.HelmDownloadJsonSchemas.Companion.DOWNLOADS
import io.github.fstaudt.helm.tasks.HelmDownloadJsonSchemas.Companion.HELM_DOWNLOAD_JSON_SCHEMAS
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@WireMockTest(httpPort = 1980)
class HelmDownloadJsonSchemasTest {
    private lateinit var testProject: TestProject

    companion object {
        const val REPOSITORY_URL = "http://localhost:1980"
        const val PATH_EXTERNAL = "charts/external-json-schema/0.1.0"
        const val PATH_EXTERNAL_0_1_1 = "charts/external-json-schema/0.1.1"
        const val PATH_REFERENCE = "charts/ref-json-schema/0.1.0"
        const val PATH_REFERENCE_0_1_1 = "charts/ref-json-schema/0.1.1"
        const val PATH_PROTECTED = "protected/protected-json-schema/0.1.0"
        const val PATH_PROTECTED_0_1_1 = "protected/protected-json-schema/0.1.1"
        const val CHART_DOWNLOADS_FOLDER = "mycharts"
        const val PROTECTED_DOWNLOADS_FOLDER = "myprotectedcharts"
        const val AUTHORIZATION_HEADER = "Basic dXNlcjpwYXNzd29yZA=="
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = listOf(
                    RepositoryMapping("@myCharts", "$REPOSITORY_URL/charts"),
                    RepositoryMapping("@myProtectedCharts", "$REPOSITORY_URL/protected", "$AUTHORIZATION_HEADER")
                  )
                }
            """.trimIndent()
            )
        }
        testProject.initHelmResources()
    }

    @BeforeEach
    fun `init wiremock`() {
        WireMock.reset()
        stubForSchemaResource("$PATH_EXTERNAL/helm-values.json")
        stubForSchemaResource("$PATH_EXTERNAL/helm-global.json")
        stubForSchemaResource("$PATH_EXTERNAL_0_1_1/helm-values.json")
        stubForSchemaResource("$PATH_EXTERNAL_0_1_1/helm-global.json")
        stubForSchemaResource("$PATH_REFERENCE/helm-values.json")
        stubForSchemaResource("$PATH_REFERENCE/helm-global.json")
        stubForSchemaResource("$PATH_REFERENCE_0_1_1/helm-global.json")
        stubForProtectedSchemaResource("$PATH_PROTECTED/helm-values.json")
        stubForProtectedSchemaResource("$PATH_PROTECTED/helm-global.json")
        stubForProtectedSchemaResource("$PATH_PROTECTED_0_1_1/helm-global.json")
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas of dependencies from JSON schema repository`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)

            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.0/helm-values.json")
            assertThatJsonFile("$downloadFolder/helm-global.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.0/helm-global.json")

            val aliasDownloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1"
            assertThatJsonFile("$aliasDownloadFolder/helm-values.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.1/helm-values.json")
            assertThatJsonFile("$aliasDownloadFolder/helm-global.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.1/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download referenced JSON schemas from JSON schema repository when $ref is relative`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)

            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile
                .hasContent().node("properties.ref-json-schema.\$ref")
                .isEqualTo("../../ref-json-schema/0.1.0/helm-values.json")

            val refDownloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_REFERENCE"
            assertThatJsonFile("$refDownloadFolder/helm-values.json").isFile
                .hasContent().and(
                    { it.node("\$id").isEqualTo("http://myCharts/ref-json-schema/0.1.0/helm-values.json") },
                    { it.node("properties.global.\$ref").isEqualTo("helm-global.json") },
                )
            assertThatJsonFile("$refDownloadFolder/helm-global.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/ref-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download referenced JSON schemas from JSON schema repository when $ref is relative with #`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)

            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile
                .hasContent().node("properties.global.properties.refs.\$ref")
                .isEqualTo("../../ref-json-schema/0.1.1/helm-global.json#/objects/refs")

            val refDownloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_REFERENCE_0_1_1"
            assertThatJsonFile("$refDownloadFolder/helm-global.json").isFile
                .hasContent().node("\$id").isEqualTo("http://myCharts/ref-json-schema/0.1.1/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas and update $ref in downloaded schema when $ref is a full URI`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)

            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile
                .hasContent().node("properties.protected-json-schema.\$ref")
                .isEqualTo("refs/$PATH_PROTECTED/helm-values.json")

            val refDownloadFolder =
                "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1/refs/$PATH_PROTECTED"
            assertThatJsonFile("$refDownloadFolder/helm-values.json").isFile
                .hasContent().node("\$id")
                .isEqualTo("http://myProtectedCharts/protected-json-schema/0.1.0/helm-values.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas and update $ref in downloaded schema when $ref is a full URI with #`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)

            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile
                .hasContent().node("properties.global.properties.configMaps.\$ref")
                .isEqualTo("refs/$PATH_PROTECTED_0_1_1/helm-global.json#/objects/configMaps")

            val refDownloadFolder =
                "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1/refs/$PATH_PROTECTED_0_1_1"
            assertThatJsonFile("$refDownloadFolder/helm-global.json").isFile
                .hasContent().node("\$id")
                .isEqualTo("http://myProtectedCharts/protected-json-schema/0.1.1/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should keep $ref starting by #`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_0_1_1"
            assertThatJsonFile("$downloadFolder/helm-global.json").isFile
                .hasContent().node("properties.configMaps.\$ref").isEqualTo("#/objects/configMaps")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas of dependencies from protected JSON schema repository`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$PROTECTED_DOWNLOADS_FOLDER/$PATH_PROTECTED"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile
                .hasContent().node("\$id")
                .isEqualTo("http://myProtectedCharts/protected-json-schema/0.1.0/helm-values.json")
            assertThatJsonFile("$downloadFolder/helm-global.json").isFile
                .hasContent().node("\$id")
                .isEqualTo("http://myProtectedCharts/protected-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should generate JSON schema with error code when JSON schema is not downloaded from repository`() {
        WireMock.reset()
        stubFor(get("/$PATH_EXTERNAL/helm-values.json").willReturn(unauthorized()))
        stubFor(get("/$PATH_EXTERNAL/helm-global.json").willReturn(notFound()))
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also { build ->
            assertThat(build.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL"
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$REPOSITORY_URL/$PATH_EXTERNAL/helm-values.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("\$error").isEqualTo("401 - Unauthorized") },
                )
            assertThatJsonFile("$downloadFolder/helm-global.json").isFile
                .hasContent().and(
                    { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                    { it.node("\$id").isEqualTo("$REPOSITORY_URL/$PATH_EXTERNAL/helm-global.json") },
                    { it.node("type").isEqualTo("object") },
                    { it.node("\$error").isEqualTo("404 - Not Found") },
                )

        }
    }

    private fun stubForSchemaResource(filePath: String) {
        stubFor(get("/$filePath").willReturn(ok().withResponseBody(bodyFrom(filePath))))
    }

    private fun stubForProtectedSchemaResource(filePath: String, authorizationHeader: String = AUTHORIZATION_HEADER) {
        stubFor(
            get("/$filePath")
                .withHeader("Authorization", equalTo(authorizationHeader))
                .willReturn(ok().withResponseBody(bodyFrom(filePath)))
        )
    }

    private fun bodyFrom(file: String) = Body(File("src/test/resources/schemas/$file").readBytes())
}