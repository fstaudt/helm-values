package io.github.fstaudt.helm.gradle.tasks

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.badRequest
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GENERATION_DIR
import io.github.fstaudt.helm.gradle.CHART_NAME
import io.github.fstaudt.helm.gradle.CHART_VERSION
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.WITH_BUILD_CACHE
import io.github.fstaudt.helm.gradle.buildDir
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.runAndFail
import io.github.fstaudt.helm.gradle.runTask
import io.github.fstaudt.helm.gradle.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.tasks.PublishJsonSchemas.Companion.PUBLISH_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

private const val REPOSITORY_PORT = 1981

@WireMockTest(httpPort = REPOSITORY_PORT)
class PublishJsonSchemasTest {
    private lateinit var testProject: TestProject

    companion object {
        private const val REPOSITORY_URL = "http://localhost:$REPOSITORY_PORT"
        private const val REPOSITORY_USER = "user"
        private const val REPOSITORY_PASSWORD = "password"
        private const val REPOSITORY_AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val BASE_CHART_PATH = "$APPS_PATH/$CHART_NAME/$CHART_VERSION"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        testProject.initHelmChart()
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
    fun `publishJsonSchemas should depend on generatedJsonSchemas`() {
        stubForSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        testProject.runTask(PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `publishJsonSchemas should publish generated JSON schemas of chart on JSON schema repository`() {
        stubForSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        testProject.runTask(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should get chart configuration in sourcesDir`() {
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        testProject.clearHelmChart()
        testProject.initHelmChart(sourcesDir)
        stubForSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        testProject.runTask(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should publish JSON schemas of chart on protected JSON schema repository`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH", "$REPOSITORY_USER", "$REPOSITORY_PASSWORD"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        stubForProtectedSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        testProject.runTask(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifyProtectedSchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should use published version from extension when it is defined`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "$APPS"
                  publishedVersion = "0.2.0"
                }
            """.trimIndent()
            )
        }
        stubForSchemaPublication("$APPS_PATH/$CHART_NAME/0.2.0/$VALUES_SCHEMA_FILE")
        testProject.runTask(PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifySchemaPublicationOf("$APPS_PATH/$CHART_NAME/0.2.0/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should fail when publication repository is not defined`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                }
            """.trimIndent()
            )
        }
        File(testProject.buildDir, "$HELM_VALUES/$GENERATION_DIR").mkdirs()
        testProject.runAndFail(PUBLISH_JSON_SCHEMAS, "-x$GENERATE_JSON_SCHEMAS").also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication repository null not found in repository mappings.")
        }
    }

    @Test
    fun `publishJsonSchemas should fail when publication repository is not found in repository mappings`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "unknown"
                }
            """.trimIndent()
            )
        }
        File(testProject.buildDir, "$HELM_VALUES/$GENERATION_DIR").mkdirs()
        testProject.runAndFail(PUBLISH_JSON_SCHEMAS, "-x$GENERATE_JSON_SCHEMAS").also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication repository unknown not found in repository mappings.")
        }
    }

    @Test
    fun `publishJsonSchemas should fail when publication repository does not allow publication`() {
        stubFor(put("/$BASE_CHART_PATH/$VALUES_SCHEMA_FILE").willReturn(badRequest()))
        testProject.runAndFail(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication of $CHART_NAME/$CHART_VERSION/$VALUES_SCHEMA_FILE failed with HTTP code 400.")
            verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }

    private fun stubForSchemaPublication(filePath: String) {
        stubFor(put("/$filePath").withRequestBody(containing(filePath)).willReturn(created()))
    }

    private fun verifySchemaPublicationOf(filePath: String) {
        verify(putRequestedFor(urlEqualTo("/$filePath")).withRequestBody(containing(filePath)))
    }

    private fun stubForProtectedSchemaPublication(filePath: String) {
        stubFor(
            put("/$filePath")
                .withRequestBody(containing(filePath))
                .withHeader("Authorization", equalTo(REPOSITORY_AUTHORIZATION))
                .willReturn(created())
        )
    }

    private fun verifyProtectedSchemaPublicationOf(filePath: String) {
        verify(
            putRequestedFor(urlEqualTo("/$filePath"))
                .withRequestBody(containing(filePath))
                .withHeader("Authorization", equalTo(REPOSITORY_AUTHORIZATION))
        )
    }
}
