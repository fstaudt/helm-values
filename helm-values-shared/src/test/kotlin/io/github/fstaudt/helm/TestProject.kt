package io.github.fstaudt.helm

import io.github.fstaudt.helm.HelmConstants.HELM_CHARTS_DIR
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.HelmConstants.HELM_VALUES_FILE
import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.ID
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import java.io.File

const val CHART_NAME = "helm-chart"
const val CHART_VERSION = "0.1.0"

typealias TestProject = File

val TestProject.buildDir get() = File(this, "build")

val TestProject.downloadSchemasDir get() = File(buildDir, DOWNLOADS_DIR)
val TestProject.extractsDir get() = File(buildDir, EXTRACTS_DIR)
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
          "$ID": "$path/$schemaFile",
          "properties": {
            "global": {}
          }
        }
    """.trimIndent()
) {
    File(this, path).mkdirs()
    File(this, "$path/$schemaFile").writeText(schemaContent)
}

fun TestProject.initExtractedHelmDependency(
    dependencyPath: String,
    chartDependencies: String? = "",
    values: String? = "",
    schema: String? = """
    {
      "$ID": "$dependencyPath/$HELM_SCHEMA_FILE",
      "properties": {
        "global": {}
      }
    }
    """.trimIndent()
) {
    File(extractsDir, dependencyPath).mkdirs()
    chartDependencies?.let {
        File(extractsDir, "$dependencyPath/$HELM_CHART_FILE").writeText("""
        apiVersion: v2
        type: application
        version: $CHART_VERSION
        description: $dependencyPath
        name: ${dependencyPath.substringAfterLast("/")}
        
        """.trimIndent())
        File(extractsDir, "$dependencyPath/$HELM_CHART_FILE").appendText(it)
    }
    values?.let { File(extractsDir, "$dependencyPath/$HELM_VALUES_FILE").writeText(it) }
    schema?.let { File(extractsDir, "$dependencyPath/$HELM_SCHEMA_FILE").writeText(it) }
}

fun TestProject.initDownloadedSchemas(
    dependencyPath: String,
    valuesSchemaFile: String = VALUES_SCHEMA_FILE,
    globalSchemaFile: String = GLOBAL_VALUES_SCHEMA_FILE,
    globalSchemaContent: String = """
        {
          "$ID": "$dependencyPath/$globalSchemaFile"
        }
    """.trimIndent(),
    valuesSchemaContent: String = """
        {
          "$ID": "$dependencyPath/$valuesSchemaFile",
          "properties": {
            "global": {
              "$REF": "$globalSchemaFile"
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
