package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.gradle.CHART_NAME
import io.github.fstaudt.helm.gradle.TestProject
import io.github.fstaudt.helm.gradle.clearHelmChart
import io.github.fstaudt.helm.gradle.exceptions.ValuesValidationException
import io.github.fstaudt.helm.gradle.initBuildFile
import io.github.fstaudt.helm.gradle.initHelmChart
import io.github.fstaudt.helm.gradle.initHelmValues
import io.github.fstaudt.helm.gradle.runAndFail
import io.github.fstaudt.helm.gradle.runTask
import io.github.fstaudt.helm.gradle.tasks.AggregateJsonSchema.Companion.AGGREGATE_JSON_SCHEMA
import io.github.fstaudt.helm.gradle.tasks.ValidateHelmValues.Companion.VALIDATE_HELM_VALUES
import io.github.fstaudt.helm.gradle.testProject
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ValidateHelmValuesTest {

    private lateinit var testProject: TestProject

    companion object {
        private const val UNEVALUATED_PROPERTY_MESSAGE = "- \$: property '%s' is not evaluated " +
                "and the schema does not allow unevaluated properties\n   (from \$.unevaluatedProperties)"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        testProject.initBuildFile()
        testProject.initHelmChart()
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `validateHelmValues should depend on aggregateJsonSchema`() {
        testProject.initHelmValues()
        testProject.runTask(VALIDATE_HELM_VALUES).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$VALIDATE_HELM_VALUES")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `validateHelmValues should fail when values are invalid`() {
        testProject.initHelmValues {
            appendText("""
                invalid: value
            """.trimIndent())
        }
        testProject.runAndFail(VALIDATE_HELM_VALUES).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$VALIDATE_HELM_VALUES")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains(ValuesValidationException::class.java.name)
            assertThat(it.output).contains(UNEVALUATED_PROPERTY_MESSAGE.format("invalid"))
        }
    }

    @Test
    fun `validateHelmValues should report multiple errors at once`() {
        testProject.initHelmValues {
            appendText("""
                invalid: value
                other: invalid value
            """.trimIndent())
        }
        testProject.runAndFail(VALIDATE_HELM_VALUES).also {
            assertThat(it.task(":$AGGREGATE_JSON_SCHEMA")!!.outcome).isEqualTo(SUCCESS)
            assertThat(it.task(":$VALIDATE_HELM_VALUES")!!.outcome).isEqualTo(FAILED)
            assertThat(it.output).contains(ValuesValidationException::class.java.name)
            assertThat(it.output)
                .contains(UNEVALUATED_PROPERTY_MESSAGE.format("invalid"))
                .contains(UNEVALUATED_PROPERTY_MESSAGE.format("other"))
        }
    }

    @Test
    fun `validateHelmValues should get Helm values file in sourcesDir`() {
        testProject.clearHelmChart()
        val sourcesDir = File(testProject, CHART_NAME).also { it.mkdirs() }
        testProject.initHelmChart(sourcesDir)
        testProject.initHelmValues(sourcesDir)
        testProject.initBuildFile {
            appendText(
                """
                helmValues {
                  sourcesDir = "$CHART_NAME"
                }
                """.trimIndent()
            )
        }
        testProject.runTask(VALIDATE_HELM_VALUES).also {
            assertThat(it.task(":$VALIDATE_HELM_VALUES")!!.outcome).isEqualTo(SUCCESS)
        }
    }

    @Test
    fun `validateHelmValues should be skipped when Helm values is missing`() {
        testProject.runTask(VALIDATE_HELM_VALUES).also {
            assertThat(it.task(":$VALIDATE_HELM_VALUES")!!.outcome).isEqualTo(NO_SOURCE)
        }
    }

    @Test
    fun `validateHelmValues should fail when there is no chart in Helm sources directory`() {
        testProject.clearHelmChart()
        testProject.initHelmValues()
        testProject.runAndFail(VALIDATE_HELM_VALUES).also {
            assertThat(it.task(":$VALIDATE_HELM_VALUES")!!.outcome).isEqualTo(FAILED)
        }
    }
}
