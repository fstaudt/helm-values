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

fun testProject(parentFolder: File = File("build/tmp")): TestProject {
    parentFolder.mkdirs()
    return File.createTempFile("junit", "", parentFolder).apply {
        delete()
        mkdir()
    }
}

fun TestProject.initLocalSchema(
    path: String,
    schemaFile: String = HELM_SCHEMA_FILE,
    schemaContent: String = """
        {
          "${'$'}id": "$path/$schemaFile",
          "properties": {
            "global": {}
          }
        }
    """.trimIndent()
) {
    File(this, path).mkdirs()
    File(this, "$path/$schemaFile").writeText(schemaContent)
}

fun TestProject.initExtractedSchemas(
    dependencyPath: String,
    schemaContent: String = """
        {
          "${'$'}id": "$dependencyPath/$HELM_SCHEMA_FILE",
          "properties": {
            "global": {}
          }
        }
    """.trimIndent()
) {
    File(extractSchemasDir, dependencyPath).mkdirs()
    File(extractSchemasDir, "$dependencyPath/$HELM_SCHEMA_FILE").writeText(schemaContent)
}

fun TestProject.initDownloadedSchemas(
    dependencyPath: String,
    valuesSchemaFile: String = VALUES_SCHEMA_FILE,
    globalSchemaFile: String = GLOBAL_VALUES_SCHEMA_FILE,
    globalSchemaContent: String = """
        {
          "${'$'}id": "$dependencyPath/$globalSchemaFile"
        }
    """.trimIndent(),
    valuesSchemaContent: String = """
        {
          "${'$'}id": "$dependencyPath/$valuesSchemaFile",
          "properties": {
            "global": {
              "${'$'}ref": "$globalSchemaFile"
            }
          }
        }
    """.trimIndent()
) {
    File(downloadSchemasDir, dependencyPath).mkdirs()
    File(downloadSchemasDir, "$dependencyPath/$valuesSchemaFile").writeText(valuesSchemaContent)
    File(downloadSchemasDir, "$dependencyPath/$globalSchemaFile").writeText(globalSchemaContent)
}

fun TestProject.initHelmResources(chartName: String, chartVersion: String) {
    File("src/test/resources/helm-resources/charts/$chartName-$chartVersion.tgz")
        .copyTo(File(chartsDir, "$chartName-$chartVersion.tgz"))
}
