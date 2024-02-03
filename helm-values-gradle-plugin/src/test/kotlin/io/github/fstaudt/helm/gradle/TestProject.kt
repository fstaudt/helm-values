package io.github.fstaudt.helm.gradle

import io.github.fstaudt.helm.HELM_CHARTS_DIR
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.HELM_VALUES_FILE
import io.github.fstaudt.helm.gradle.HelmValuesExtension.Companion.HELM_SOURCES_DIR
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

const val WITH_BUILD_CACHE = "--build-cache"
const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"

typealias TestProject = File

val TestProject.buildDir get() = File(this, "build")

fun testProject(parentFolder: File? = File("build/tmp")): TestProject {
    parentFolder?.mkdirs()
    return File.createTempFile("junit", "", parentFolder).apply {
        delete()
        mkdir()
        initSettingsFile()
        initBuildFile()
    }
}

private fun TestProject.initSettingsFile(): File {
    return File(this, "settings.gradle.kts").apply {
        writeText("rootProject.name = \"test-helm-chart\"")
    }
}

fun TestProject.initBuildFile(customizeBuildFile: File.() -> Unit = {}): File {
    return File(this, "build.gradle.kts").apply {
        writeText(
            """
                import io.github.fstaudt.helm.model.JsonSchemaRepository
                
                plugins {
                  id("io.github.fstaudt.helm-values")
                }
            """.trimIndent()
        )
        customizeBuildFile()
    }
}

fun TestProject.initHelmResources(helmSourcesDirectory: String = HELM_SOURCES_DIR) {
    File("src/test/resources/helm-resources").copyRecursively(File(this, helmSourcesDirectory))
}

fun TestProject.initHelmResources(
    chartName: String,
    chartVersion: String = "0.1.0",
    helmSourcesDirectory: String = HELM_SOURCES_DIR,
) {
    File("src/test/resources/helm-resources/charts/$chartName-$chartVersion.tgz")
        .copyTo(File(this, "$helmSourcesDirectory/$HELM_CHARTS_DIR/$chartName-$chartVersion.tgz"))
}

fun TestProject.initHelmChart(sourcesDir: File = this, customizeHelmChart: File.() -> Unit = {}): File {
    return File(sourcesDir, HELM_CHART_FILE).apply {
        writeText(
            """
            apiVersion: v2
            name: $CHART_NAME
            version: $CHART_VERSION
            description: Test helm chart
            maintainers:
              - name: François Staudt
                url: https://github.com/fstaudt
            icon: https://helm.sh/img/helm.svg
            
        """.trimIndent()
        )
        customizeHelmChart()
    }
}

fun TestProject.initHelmValues(sourcesDir: File = this, customizeHelmValues: File.() -> Unit = {}): File {
    return File(sourcesDir, HELM_VALUES_FILE).apply {
        writeText("")
        customizeHelmValues()
    }
}

fun TestProject.clearHelmChart(sourcesDir: File = this) {
    File(sourcesDir, HELM_CHART_FILE).delete()
}

fun TestProject.initHelmChart(customizeHelmChart: File.() -> Unit = {}): File {
    return initHelmChart(this, customizeHelmChart)
}

fun TestProject.initHelmValues(customizeHelmValues: File.() -> Unit): File {
    return initHelmValues(this, customizeHelmValues)
}

fun TestProject.runTask(vararg task: String): BuildResult {
    return gradleRunner(*task).build()
}

fun TestProject.runAndFail(vararg task: String): BuildResult {
    return gradleRunner(*task).buildAndFail()
}

private fun TestProject.gradleRunner(vararg task: String): GradleRunner {
    return GradleRunner.create()
        .withProjectDir(this)
        .withArguments("--info", "--stacktrace", "--warning-mode=fail", *task)
        .withPluginClasspath()
        .withDebug(true)
        .withGradleVersion(System.getProperty("testGradleVersion"))
        .forwardOutput()
}
