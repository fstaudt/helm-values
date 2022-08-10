package io.github.fstaudt.helm

import java.io.File

const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"
const val DOWNLOADS = "downloads"
const val EXTRACT = "extract"
const val CHARTS = "charts"

typealias TestProject = File

val TestProject.buildDir get() = File(this, "build")

val TestProject.downloadSchemasDir get() = File(buildDir, DOWNLOADS)
val TestProject.extractSchemasDir get() = File(buildDir, EXTRACT)
val TestProject.chartsDir get() = File(this, CHARTS)

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
