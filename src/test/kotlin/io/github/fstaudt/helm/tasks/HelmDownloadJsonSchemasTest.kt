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

@WireMockTest(httpPort = 1080)
class HelmDownloadJsonSchemasTest {
    private lateinit var testProject: TestProject

    companion object {
        const val REPOSITORY_URL = "http://localhost:1080"
        const val PATH_EXTERNAL = "charts/external-json-schema/0.1.0"
        const val PATH_EXTERNAL_ALIAS = "charts/external-json-schema/0.1.1"
        const val PATH_PROTECTED = "protected/protected-json-schema/0.1.0"
        const val CHART_DOWNLOADS_FOLDER = "my-charts"
        const val PROTECTED_DOWNLOADS_FOLDER = "my-protected-charts"
        const val AUTHORIZATION_HEADER = "Basic dXNlcjpwYXNzd29yZA=="
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "@myCharts" to RepositoryMapping("$CHART_DOWNLOADS_FOLDER", "$REPOSITORY_URL/charts"),
                    "@myProtectedCharts" to RepositoryMapping("$PROTECTED_DOWNLOADS_FOLDER", "$REPOSITORY_URL/protected", "$AUTHORIZATION_HEADER")
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
        stubForSchemaResource("$PATH_EXTERNAL_ALIAS/helm-values.json")
        stubForSchemaResource("$PATH_EXTERNAL_ALIAS/helm-global.json")
        stubForProtectedSchemaResource("$PATH_PROTECTED/helm-values.json")
        stubForProtectedSchemaResource("$PATH_PROTECTED/helm-global.json")
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas of dependencies from JSON schema repository`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val externalDownloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL"
            assertThatJsonFile("$externalDownloadFolder/helm-values.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.0/helm-values.json")
            assertThatJsonFile("$externalDownloadFolder/helm-global.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.0/helm-global.json")
            val aliasDownloadFolder = "${testProject.buildDir}/$DOWNLOADS/$CHART_DOWNLOADS_FOLDER/$PATH_EXTERNAL_ALIAS"
            assertThatJsonFile("$aliasDownloadFolder/helm-values.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.1/helm-values.json")
            assertThatJsonFile("$aliasDownloadFolder/helm-global.json").isFile.hasContent()
                .node("\$id").isEqualTo("http://myCharts/external-json-schema/0.1.1/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas of dependencies from protected JSON schema repository`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$DOWNLOADS/$PROTECTED_DOWNLOADS_FOLDER/$PATH_PROTECTED"
            assertThat(File("$downloadFolder/helm-values.json")).isFile
                .content().contains("http://myProtectedCharts/protected-json-schema/0.1.0/helm-values.json")
            assertThat(File("$downloadFolder/helm-global.json")).isFile
                .content().contains("http://myProtectedCharts/protected-json-schema/0.1.0/helm-global.json")
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
            assertThatJsonFile("$downloadFolder/helm-values.json").isFile.hasContent().and(
                { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                { it.node("\$id").isEqualTo("$REPOSITORY_URL/$PATH_EXTERNAL/helm-values.json") },
                { it.node("type").isEqualTo("object") },
                { it.node("\$error").isEqualTo("401 - Unauthorized") }
            )
            assertThatJsonFile("$downloadFolder/helm-global.json").isFile.hasContent().and(
                { it.node("\$schema").isEqualTo("https://json-schema.org/draft/2020-12/schema") },
                { it.node("\$id").isEqualTo("$REPOSITORY_URL/$PATH_EXTERNAL/helm-global.json") },
                { it.node("type").isEqualTo("object") },
                { it.node("\$error").isEqualTo("404 - Not Found") }
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