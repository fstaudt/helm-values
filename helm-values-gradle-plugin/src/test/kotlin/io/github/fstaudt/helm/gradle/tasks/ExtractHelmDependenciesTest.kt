package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_VALUES_FILE
import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.gradle.CHART_NAME
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.WITH_BUILD_CACHE
import io.github.fstaudt.helm.gradle.buildDir
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.initHelmResources
import io.github.fstaudt.helm.gradle.runTask
import io.github.fstaudt.helm.gradle.tasks.ExtractHelmDependencies.Companion.EXTRACT_HELM_DEPENDENCIES
import io.github.fstaudt.helm.gradle.testProject
import io.github.fstaudt.helm.test.assertions.JsonFileAssert.Companion.assertThatJsonFile
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ExtractHelmDependenciesTest {
    private lateinit var testProject: TestProject
    private lateinit var extractSchemasDir: File

    companion object {
        private const val THIRDPARTY = "@thirdparty"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        extractSchemasDir = File(testProject.buildDir, "$HELM_VALUES/$EXTRACTS_DIR")
        testProject.initHelmResources()
        testProject.initBuildFile()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `extractHelmDependencies should extract JSON schemas from dependency archives`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(EXTRACT_HELM_DEPENDENCIES).also {
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/$HELM_SCHEMA_FILE")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
                .content().contains("name: $EMBEDDED_SCHEMA")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
                .content().contains("key: \"key\"")
        }
    }

    @Test
    fun `extractHelmDependencies should get chart dependencies in sourcesDir`() {
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                }
            """.trimIndent()
            )
        }
        File(testProject, "charts").deleteRecursively()
        testProject.initHelmResources(CHART_NAME)
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir) {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(EXTRACT_HELM_DEPENDENCIES).also {
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(SUCCESS)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/$HELM_SCHEMA_FILE")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
                .content().contains("name: $EMBEDDED_SCHEMA")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
                .content().contains("key: \"key\"")
        }
    }

    @Test
    fun `extractHelmDependencies should retrieve JSON schemas from cache on second run`() {
        testProject.initHelmChart {
            appendText(
                """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: 0.1.0
                  repository: "$THIRDPARTY"
                """.trimIndent()
            )
        }
        testProject.runTask(WITH_BUILD_CACHE, EXTRACT_HELM_DEPENDENCIES).also {
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isIn(SUCCESS, FROM_CACHE)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/$HELM_SCHEMA_FILE")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
                .content().contains("name: $EMBEDDED_SCHEMA")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
                .content().contains("key: \"key\"")
        }
        File("$extractSchemasDir").deleteRecursively()
        testProject.runTask(WITH_BUILD_CACHE, EXTRACT_HELM_DEPENDENCIES).also {
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(FROM_CACHE)
            assertThatJsonFile("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE").isFile
                .hasContent().node("\$id").isEqualTo("$EMBEDDED_SCHEMA/0.1.0/$HELM_SCHEMA_FILE")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_CHART_FILE")).isFile
                .content().contains("name: $EMBEDDED_SCHEMA")
            assertThat(File("$extractSchemasDir/$EMBEDDED_SCHEMA/$HELM_VALUES_FILE")).isFile
                .content().contains("key: \"key\"")
        }
    }

    @Test
    fun `extractHelmDependencies should be skipped when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.runTask(EXTRACT_HELM_DEPENDENCIES).also {
            assertThat(it.task(":$EXTRACT_HELM_DEPENDENCIES")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }
}
