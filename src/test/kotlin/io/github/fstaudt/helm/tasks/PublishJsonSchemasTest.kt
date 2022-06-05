package io.github.fstaudt.helm.tasks

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
import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.WITH_BUILD_CACHE
import io.github.fstaudt.helm.buildDir
import io.github.fstaudt.helm.initBuildFile
import io.github.fstaudt.helm.initHelmChart
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.model.JsonSchemaRepository.Companion.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.runAndFail
import io.github.fstaudt.helm.runTask
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATED
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.PublishJsonSchemas.Companion.PUBLISH_JSON_SCHEMAS
import io.github.fstaudt.helm.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@WireMockTest(httpPort = 1980)
class PublishJsonSchemasTest {
    private lateinit var testProject: TestProject

    companion object {
        private const val REPOSITORY_URL = "http://localhost:1980"
        private const val REPOSITORY_USER = "user"
        private const val REPOSITORY_PASSWORD = "password"
        private const val REPOSITORY_AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="
        private const val UNAVAILABLE_URL = "http://localhost:1981"
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
                helmValuesAssistant {
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
        stubForSchemaPublication("$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE")
        testProject.runTask(PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$GENERATE_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `publishJsonSchemas should publish generated JSON schemas of chart on JSON schema repository`() {
        stubForSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        stubForSchemaPublication("$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE")
        testProject.runTask(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
            verifySchemaPublicationOf("$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should publish JSON schemas of chart on protected JSON schema repository`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH", "$REPOSITORY_USER", "$REPOSITORY_PASSWORD"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        stubForProtectedSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        stubForProtectedSchemaPublication("$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE")
        testProject.runTask(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifyProtectedSchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
            verifyProtectedSchemaPublicationOf("$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should use published version from extension when it is defined`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
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
        stubForSchemaPublication("$APPS_PATH/$CHART_NAME/0.2.0/$GLOBAL_VALUES_SCHEMA_FILE")
        testProject.runTask(PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(SUCCESS)
            verifySchemaPublicationOf("$APPS_PATH/$CHART_NAME/0.2.0/$VALUES_SCHEMA_FILE")
            verifySchemaPublicationOf("$APPS_PATH/$CHART_NAME/0.2.0/$GLOBAL_VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should fail when publication repository is not defined`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                }
            """.trimIndent()
            )
        }
        File(testProject.buildDir, GENERATED).mkdirs()
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
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"),
                  )
                  publicationRepository = "unknown"
                }
            """.trimIndent()
            )
        }
        File(testProject.buildDir, GENERATED).mkdirs()
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
    fun `publishJsonSchemas should fail when publication repository does not allow publication of global-values schema`() {
        stubForSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        stubFor(put("/$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE").willReturn(badRequest()))
        testProject.runAndFail(WITH_BUILD_CACHE, PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication of $CHART_NAME/$CHART_VERSION/$GLOBAL_VALUES_SCHEMA_FILE failed with HTTP code 400.")
            verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
            verifySchemaPublicationOf("$BASE_CHART_PATH/$GLOBAL_VALUES_SCHEMA_FILE")
        }
    }

    @Test
    fun `publishJsonSchemas should fail when publication repository is unreachable`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValuesAssistant {
                  repositoryMappings = mapOf(
                    "$APPS" to JsonSchemaRepository("$UNAVAILABLE_URL"),
                  )
                  publicationRepository = "$APPS"
                }
            """.trimIndent()
            )
        }
        testProject.runAndFail(PUBLISH_JSON_SCHEMAS).also {
            assertThat(it.task(":$PUBLISH_JSON_SCHEMAS")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains("Publication of $CHART_NAME/$CHART_VERSION/$VALUES_SCHEMA_FILE failed with HTTP code 0.")
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