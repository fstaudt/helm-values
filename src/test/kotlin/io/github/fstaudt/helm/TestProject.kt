package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmValuesAssistantExtension.Companion.HELM_SOURCES_DIRECTORY
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

const val WITH_BUILD_CACHE = "--build-cache"

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
        writeText("rootProject.name = \"gradle-hugo-plugin-test\"")
    }
}

fun TestProject.initBuildFile(customizeBuildFile: File.() -> Unit = {}): File {
    return File(this, "build.gradle.kts").apply {
        writeText("""
                import io.github.fstaudt.helm.model.RepositoryMapping;
                
                plugins {
                  id("io.github.fstaudt.helm-values-assistant")
                }
            """.trimIndent())
        customizeBuildFile()
    }
}

fun TestProject.initHelmResources(helmSourcesDirectory: String = HELM_SOURCES_DIRECTORY) {
    File("src/test/resources/helm-resources").copyRecursively(File(this, helmSourcesDirectory))
}

fun TestProject.initHelmChart(customizeHelmChart: File.() -> Unit = {}): File {
    return File(this, "Chart.yaml").apply {
        writeText("""
            apiVersion: v2
            name: helm-chart
            version: 0.1.0
            description: Test helm chart
            maintainers:
              - name: François Staudt
                url: https://github.com/fstaudt
            icon: https://helm.sh/img/helm.svg
            
        """.trimIndent())
        customizeHelmChart()
    }
}

fun TestProject.runTask(vararg task: String): BuildResult {
    return gradleRunner(*task).build()
}

private fun TestProject.gradleRunner(vararg task: String): GradleRunner {
    return GradleRunner.create()
            .withProjectDir(this)
            .withArguments("--info", "--stacktrace", *task)
            .withPluginClasspath()
            .withDebug(true)
            .forwardOutput()
}
