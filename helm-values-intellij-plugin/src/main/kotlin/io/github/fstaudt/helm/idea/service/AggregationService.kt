package io.github.fstaudt.helm.idea.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.*
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.io.File

class AggregationService {
    companion object {
        const val JSON_SCHEMAS_DIR = ".idea/json-schemas"
        val instance: AggregationService =
            ApplicationManager.getApplication().getService(AggregationService::class.java)
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
        val jsonSchemasDir = File(project.baseDir(), "$JSON_SCHEMAS_DIR/${chart.name}")
        val downloadSchemaDir = download(jsonSchemasDir, chart)
        val extractSchemaDir = extract(jsonSchemasDir, File(chartFile.parentFile, "charts"), chart)
        val aggregator = JsonSchemaAggregator(mappings(), downloadSchemaDir, extractSchemaDir)
        aggregator.aggregate(chart, jsonPatch(chartFile)).also {
            jsonMapper.writeValue(File(jsonSchemasDir, AGGREGATED_SCHEMA_FILE), it)
        }
    }

    private fun download(jsonSchemasDir: File, chart: Chart) = File(jsonSchemasDir, DOWNLOADS_DIR).also {
        JsonSchemaDownloader(mappings(), it).download(chart)
    }

    private fun extract(jsonSchemasDir: File, chartsDir: File, chart: Chart) = File(jsonSchemasDir, EXTRACT_DIR).also {
        JsonSchemaExtractor(chartsDir, it).extract(chart)
    }

    private fun jsonPatch(chartFile: File): JsonPatch? {
        return File(chartFile.parent, PATCH_AGGREGATED_SCHEMA_FILE).takeIf { it.exists() }?.let {
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
