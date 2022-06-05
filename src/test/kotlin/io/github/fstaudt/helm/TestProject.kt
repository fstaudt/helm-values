package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmValuesAssistantExtension.Companion.HELM_SOURCES_DIR
import io.github.fstaudt.helm.tasks.UnpackJsonSchemas.Companion.CHARTS_DIR
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

const val WITH_BUILD_CACHE = "--build-cache"
const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"
const val REPOSITORY_PORT = 1980
const val REPOSITORY_URL = "http://localhost:$REPOSITORY_PORT"
const val REPOSITORY_USER = "user"
const val REPOSITORY_PASSWORD = "password"
const val REPOSITORY_AUTHORIZATION = "Basic dXNlcjpwYXNzd29yZA=="

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
                import io.github.fstaudt.helm.model.JsonSchemaRepository;
                
                plugins {
                  id("io.github.fstaudt.helm-values-assistant")
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
        .copyTo(File(this, "$helmSourcesDirectory/$CHARTS_DIR/$chartName-$chartVersion.tgz"))
}

fun TestProject.initHelmChart(customizeHelmChart: File.() -> Unit = {}): File {
    return File(this, "Chart.yaml").apply {
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

fun TestProject.runTask(vararg task: String): BuildResult {
    return gradleRunner(*task).build()
}

fun TestProject.runAndFail(vararg task: String): BuildResult {
    return gradleRunner(*task).buildAndFail()
}

private fun TestProject.gradleRunner(vararg task: String): GradleRunner {
    return GradleRunner.create()
        .withProjectDir(this)
        .withArguments("--info", "--stacktrace", *task)
        .withPluginClasspath()
        .withDebug(true)
        .forwardOutput()
}
