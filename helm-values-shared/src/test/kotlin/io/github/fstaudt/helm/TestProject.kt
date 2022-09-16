package io.github.fstaudt.helm

import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import java.io.File

const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"

typealias TestProject = File

val TestProject.buildDir get() = File(this, "build")

val TestProject.downloadSchemasDir get() = File(buildDir, DOWNLOADS_DIR)
val TestProject.extractSchemasDir get() = File(buildDir, EXTRACT_DIR)
val TestProject.chartsDir get() = File(this, HELM_CHARTS_DIR)

fun testProject(parentFolder: File? = File("build/tmp")): TestProject {
    parentFolder?.mkdirs()
    return File.createTempFile("junit", "", parentFolder).apply {
        delete()
        mkdir()
    }
}

fun TestProject.initDownloadedSchemas(dependencyName: String) {
    File(downloadSchemasDir, dependencyName).mkdirs()
    File(downloadSchemasDir, "$dependencyName/$VALUES_SCHEMA_FILE").writeText("{}")
    File(downloadSchemasDir, "$dependencyName/$GLOBAL_VALUES_SCHEMA_FILE").writeText("{}")
}

fun TestProject.initExtractedSchemas(dependencyName: String) {
    File(extractSchemasDir, dependencyName).mkdirs()
    File(extractSchemasDir, "$dependencyName/$HELM_SCHEMA_FILE").writeText("{}")
}

fun TestProject.initHelmResources(chartName: String, chartVersion: String) {
    File("src/test/resources/helm-resources/charts/$chartName-$chartVersion.tgz")
        .copyTo(File(chartsDir, "$chartName-$chartVersion.tgz"))
}
