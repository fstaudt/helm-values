package io.github.fstaudt.helm.http

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.Keywords.Companion.ID
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.exceptions.PublicationException
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.testProject
import org.apache.hc.client5.http.HttpHostConnectException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

private const val REPOSITORY_PORT = 1983

@WireMockTest(httpPort = REPOSITORY_PORT)
internal class NexusRawJsonSchemaPublisherTest {
    private lateinit var testProject: TestProject
    private val chart = Chart("v2", CHART_NAME, CHART_VERSION)
    private lateinit var schemaFile: File
    private val publisher = NexusRawJsonSchemaPublisher()

    companion object {
        private const val REPOSITORY_URL = "http://localhost:$REPOSITORY_PORT"
        private const val REPOSITORY_USER = "user"
        private const val REPOSITORY_PASSWORD = "password"
        private const val REPOSITORY_AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="
        private const val UNAVAILABLE_URL = "http://localhost:1981"
        private const val APPS_PATH = "apps"
        private const val BASE_CHART_PATH = "$APPS_PATH/$CHART_NAME/$CHART_VERSION"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        schemaFile = File(testProject, VALUES_SCHEMA_FILE).also {
            it.writeText("""
            {
              "$ID": "$BASE_CHART_PATH/$VALUES_SCHEMA_FILE"
            }
        """.trimIndent())
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
    fun `publish should publish JSON schema file on JSON schema repository`() {
        stubForSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        val repository = JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH")
        publisher.publish(repository, chart, schemaFile)
        verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `publish should publish JSON schema file on protected JSON schema repository`() {
        stubForProtectedSchemaPublication("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
        val repository = JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH", REPOSITORY_USER, REPOSITORY_PASSWORD)
        publisher.publish(repository, chart, schemaFile)
        verifyProtectedSchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `publish should fail when publication repository does not allow publication`() {
        WireMock.stubFor(put("/$BASE_CHART_PATH/$VALUES_SCHEMA_FILE").willReturn(WireMock.badRequest()))
        val repository = JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH")
        assertThatThrownBy { publisher.publish(repository, chart, schemaFile) }
            .isInstanceOf(PublicationException::class.java)
            .hasMessage("Publication of $CHART_NAME/$CHART_VERSION/$VALUES_SCHEMA_FILE failed with HTTP code 400.")
        verifySchemaPublicationOf("$BASE_CHART_PATH/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `publishJsonSchemas should fail when publication repository is unreachable`() {
        val repository = JsonSchemaRepository(UNAVAILABLE_URL)
        assertThatThrownBy { publisher.publish(repository, chart, schemaFile) }
            .isInstanceOf(PublicationException::class.java)
            .hasMessage("Publication of $CHART_NAME/$CHART_VERSION/$VALUES_SCHEMA_FILE failed with HTTP code 0.")
            .hasCauseInstanceOf(HttpHostConnectException::class.java)
    }

    private fun stubForSchemaPublication(filePath: String) {
        WireMock.stubFor(put("/$filePath").withRequestBody(containing(filePath)).willReturn(created()))
    }

    private fun verifySchemaPublicationOf(filePath: String) {
        WireMock.verify(putRequestedFor(urlEqualTo("/$filePath")).withRequestBody(containing(filePath)))
    }

    private fun stubForProtectedSchemaPublication(filePath: String) {
        WireMock.stubFor(
            put("/$filePath")
                .withRequestBody(containing(filePath))
                .withHeader("Authorization", equalTo(REPOSITORY_AUTHORIZATION))
                .willReturn(created())
        )
    }

    private fun verifyProtectedSchemaPublicationOf(filePath: String) {
        WireMock.verify(
            putRequestedFor(urlEqualTo("/$filePath"))
                .withRequestBody(containing(filePath))
                .withHeader("Authorization", equalTo(REPOSITORY_AUTHORIZATION))
        )
    }
}
