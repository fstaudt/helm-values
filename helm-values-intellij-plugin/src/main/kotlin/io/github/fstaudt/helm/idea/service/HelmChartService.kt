package io.github.fstaudt.helm.idea.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_CHART_FILE
import io.github.fstaudt.helm.JsonSchemaAggregator
import io.github.fstaudt.helm.JsonSchemaDownloader
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.io.File

class HelmChartService {
    companion object {
        const val JSON_SCHEMAS_DIR = ".idea/json-schemas"
        const val CHART_METADATA_FILE = "metadata.yaml"
        val instance: HelmChartService = ApplicationManager.getApplication().getService(HelmChartService::class.java)
    }

    private val jsonSchemaRepositoryMappingService = JsonSchemaRepositoryMappingService.instance

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(INDENT_OUTPUT)
    }

    fun aggregate(project: Project, chartFile: File) {
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        chart.dependencies.filter { it.isStoredLocally() }.forEach {
            aggregate(project, File(chartFile.parentFile, "${it.localPath()}/$HELM_CHART_FILE"))
        }
        val jsonSchemasDir = File(project.baseDir(), "$JSON_SCHEMAS_DIR/${chart.name}")
        val downloadSchemaDir = download(jsonSchemasDir, chart)
        val extractSchemaDir = extract(jsonSchemasDir, File(chartFile.parentFile, "charts"), chart)
        val aggregator = JsonSchemaAggregator(
            mappings(),
            IntellijSchemaLocator(chartFile.parentFile, project.baseDir()),
            chartFile.parentFile,
            downloadSchemaDir,
            extractSchemaDir)
        val aggregatedJsonPatch = jsonPatch(chartFile, PATCH_AGGREGATED_SCHEMA_FILE)
        val valuesJsonPatch = jsonPatch(chartFile, PATCH_VALUES_SCHEMA_FILE)
        aggregator.aggregate(chart, valuesJsonPatch, aggregatedJsonPatch).also {
            jsonMapper.writeValue(File(jsonSchemasDir, AGGREGATED_SCHEMA_FILE), it)
        }
        HelmChartMetadata(chartFile.parentFile).also {
            yamlMapper.writeValue(File(jsonSchemasDir, CHART_METADATA_FILE), it)
        }
    }

    fun clear(project: Project, chartFile: File) {
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        File(project.baseDir(), "$JSON_SCHEMAS_DIR/${chart.name}").deleteRecursively()
    }

    private fun download(jsonSchemasDir: File, chart: Chart) = File(jsonSchemasDir, DOWNLOADS_DIR).also {
        JsonSchemaDownloader(mappings(), it).download(chart)
    }

    private fun extract(jsonSchemasDir: File, chartsDir: File, chart: Chart) = File(jsonSchemasDir, EXTRACT_DIR).also {
        JsonSchemaExtractor(chartsDir, mappings(), it).extract(chart)
    }

    private fun jsonPatch(chartFile: File, patchFile: String): JsonPatch? {
        return File(chartFile.parent, patchFile).takeIf { it.exists() }?.let {
            JsonPatch.fromJson(jsonMapper.readTree(it))
        }
    }

    private fun mappings(): Map<String, JsonSchemaRepository> {
        return jsonSchemaRepositoryMappingService.list()
            .associateBy { it.name }.mapValues { it.value.toJsonSchemaRepository() }
    }

    private fun JsonSchemaRepositoryMapping.toJsonSchemaRepository(): JsonSchemaRepository {
        return JsonSchemaRepository(baseUri, username, password, valuesSchemaFile, globalValuesSchemaFile)
    }
}
