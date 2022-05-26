package io.github.fstaudt.helm.tasks

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
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

    companion object {
        private const val DOWNLOADS_EXTERNAL_CHART = "$HELM_VALUES/$DOWNLOADS/sub-chart-with-external-json-schema"
        const val DOWNLOADED_EXTERNAL_VALUES_SCHEMA = "$DOWNLOADS_EXTERNAL_CHART/helm-values.json"
        const val DOWNLOADED_EXTERNAL_GLOBAL_SCHEMA = "$DOWNLOADS_EXTERNAL_CHART/helm-global.json"
        private const val DOWNLOADS_EMBEDDED_CHART = "$HELM_VALUES/$DOWNLOADS/sub-chart-with-embedded-json-schema"
        const val DOWNLOADED_EMBEDDED_VALUES_SCHEMA = "$DOWNLOADS_EMBEDDED_CHART/helm-values.json"
        const val DOWNLOADED_EMBEDDED_GLOBAL_SCHEMA = "$DOWNLOADS_EMBEDDED_CHART/helm-global.json"
        private const val DOWNLOADS_ALIAS_CHART = "$HELM_VALUES/$DOWNLOADS/sub-chart-alias"
        const val DOWNLOADED_ALIAS_VALUES_SCHEMA = "$DOWNLOADS_ALIAS_CHART/helm-values.json"
        const val DOWNLOADED_ALIAS_GLOBAL_SCHEMA = "$DOWNLOADS_ALIAS_CHART/helm-global.json"
    }

    @BeforeEach
    fun `init wiremock`() {
        stubFor(get("/charts/sub-chart-with-external-json-schema/0.1.0/helm-values.json")
                .willReturn(ok().withResponseBody(bodySchema("sub-chart-with-external-json-schema/helm-values.json"))))
        stubFor(get("/charts/sub-chart-with-external-json-schema/0.1.0/helm-global.json")
                .willReturn(ok().withResponseBody(bodySchema("sub-chart-with-external-json-schema/helm-global.json"))))
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText("""
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "@mycharts" to io.github.fstaudt.helm.model.RepositoryMapping("http://localhost:1080/charts")
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
    fun `downloadJsonSchema should download JSON schemas of dependencies from JSON schema repository in build folder`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_EXTERNAL_VALUES_SCHEMA")).isFile
                    .content().contains("http://mycharts.schema.repository/sub-chart-with-external-json-schema/0.1.0/helm-values.json")
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_EXTERNAL_GLOBAL_SCHEMA")).isFile
                    .content().contains("http://mycharts.schema.repository/sub-chart-with-external-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `downloadJsonSchema should use dependency alias as download folder in build folder`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_ALIAS_VALUES_SCHEMA")).isFile
                    .content().contains("http://mycharts.schema.repository/sub-chart-with-external-json-schema/0.1.0/helm-values.json")
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_ALIAS_GLOBAL_SCHEMA")).isFile
                    .content().contains("http://mycharts.schema.repository/sub-chart-with-external-json-schema/0.1.0/helm-global.json")
        }
    }

    @Test
    fun `downloadJsonSchema should generate empty JSON schema in build folder when JSON schema is not found in repository`() {
        WireMock.reset()
        stubFor(get("/charts/sub-chart-with-external-json-schema/0.1.0/helm-values.json").willReturn(notFound()))
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_EXTERNAL_VALUES_SCHEMA")).isFile.hasContent("{}")
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_EXTERNAL_GLOBAL_SCHEMA")).isFile.hasContent("{}")
        }
    }

    @Test
    fun `downloadJsonSchema should generate empty JSON schema in build folder when repository mapping is not configured`() {
        testProject.runTask(WITH_BUILD_CACHE, HELM_DOWNLOAD_JSON_SCHEMAS).also {
            assertThat(it.task(":$HELM_DOWNLOAD_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_EMBEDDED_VALUES_SCHEMA")).isFile.hasContent("{}")
            assertThat(File("${testProject.buildDir}/$DOWNLOADED_EMBEDDED_GLOBAL_SCHEMA")).isFile.hasContent("{}")
        }
    }

    private fun bodySchema(file: String) = Body(File("src/test/resources/schemas/$file").readBytes())
}