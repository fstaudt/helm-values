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
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
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

    @BeforeEach
    fun `init wiremock`() {
        stubFor(get("/charts/external-json-schema/0.1.0/helm-values.json")
                .willReturn(ok().withResponseBody(bodySchema("external-json-schema/helm-values.json"))))
        stubFor(get("/charts/external-json-schema/0.1.0/helm-global.json")
                .willReturn(ok().withResponseBody(bodySchema("external-json-schema/helm-global.json"))))
        stubFor(get("/protected-charts/protected-json-schema/0.1.0/helm-values.json")
                .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNzd29yZA=="))
                .willReturn(ok().withResponseBody(bodySchema("protected-json-schema/helm-values.json"))))
        stubFor(get("/protected-charts/protected-json-schema/0.1.0/helm-global.json")
                .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNzd29yZA=="))
                .willReturn(ok().withResponseBody(bodySchema("protected-json-schema/helm-global.json"))))
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText("""
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "@myCharts" to RepositoryMapping("http://localhost:1080/charts"),
                    "@myProtectedCharts" to RepositoryMapping("http://localhost:1080/protected-charts", "Basic dXNlcjpwYXNzd29yZA==")
                  )
                }
            """.trimIndent())
        }
        testProject.initHelmResources()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
        WireMock.reset()
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas of dependencies from JSON schema repository`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS/external-json-schema"
            assertThat(File("$downloadFolder/helm-values.json")).isFile
                    .content().contains("http://myCharts/external-json-schema/0.1.0/helm-values.json")
            assertThat(File("$downloadFolder/helm-global.json")).isFile
                    .content().contains("http://myCharts/external-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should download JSON schemas of dependencies from protected JSON schema repository`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS/protected-json-schema"
            assertThat(File("$downloadFolder/helm-values.json")).isFile
                    .content().contains("http://myProtectedCharts/protected-json-schema/0.1.0/helm-values.json")
            assertThat(File("$downloadFolder/helm-global.json")).isFile
                    .content().contains("http://myProtectedCharts/protected-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should use dependency alias as download folder`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS/external-json-schema-alias"
            assertThat(File("$downloadFolder/helm-values.json")).isFile
                    .content().contains("http://myCharts/external-json-schema/0.1.0/helm-values.json")
            assertThat(File("$downloadFolder/helm-global.json")).isFile
                    .content().contains("http://myCharts/external-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should generate JSON schema with error code when JSON schema is not downloaded from repository`() {
        WireMock.reset()
        stubFor(get("/charts/external-json-schema/0.1.0/helm-values.json").willReturn(unauthorized()))
        stubFor(get("/charts/external-json-schema/0.1.0/helm-global.json").willReturn(notFound()))
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS/external-json-schema"
            assertThat(File("$downloadFolder/helm-values.json")).isFile.hasContent("{\"errorCode\":\"401\"}")
            assertThat(File("$downloadFolder/helm-global.json")).isFile.hasContent("{\"errorCode\":\"404\"}")
        }
    }

    @Test
    fun `helmDownloadJsonSchema should generate empty JSON schema when repository mapping is not configured`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            val downloadFolder = "${testProject.buildDir}/$HELM_VALUES/$DOWNLOADS/embedded-json-schema"
            assertThat(File("$downloadFolder/helm-values.json")).isFile.hasContent("{}")
            assertThat(File("$downloadFolder/helm-global.json")).isFile.hasContent("{}")
        }
    }

    private fun bodySchema(file: String) = Body(File("src/test/resources/schemas/$file").readBytes())
}