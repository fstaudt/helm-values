package io.github.fstaudt.helm.idea.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.fge.jsonpatch.JsonPatch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.fstaudt.helm.HelmConstants.HELM_CHART_FILE
import io.github.fstaudt.helm.HelmDependencyExtractor
import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.PATCH_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaDownloader
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.model.HelmChartMetadata
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.io.File

@Service
class HelmJsonSchemaService {
    companion object {
        const val JSON_SCHEMAS_DIR = ".idea/json-schemas"
        const val CHART_METADATA_FILE = "metadata.yaml"
        val instance: HelmJsonSchemaService =
            ApplicationManager.getApplication().getService(HelmJsonSchemaService::class.java)
    }

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(INDENT_OUTPUT)
    }

    fun aggregate(project: Project, chartFile: File, updateLocalDependencies: Boolean = true): Boolean {
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        if (updateLocalDependencies) {
            chart.dependencies.filter { it.isStoredLocally() }.forEach { dependency ->
                File(chartFile.parentFile, "${dependency.localPath()}/$HELM_CHART_FILE").canonicalFile
                    .takeIf { it.exists() }?.let { aggregate(project, it) }
            }
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
        return chart.dependencies.all { File(extractSchemaDir, "${it.name}/$HELM_CHART_FILE").exists() }
    }

    fun clear(project: Project, chartFile: File) {
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        File(project.baseDir(), "$JSON_SCHEMAS_DIR/${chart.name}").deleteRecursively()
    }

    private fun download(jsonSchemasDir: File, chart: Chart) = File(jsonSchemasDir, DOWNLOADS_DIR).also {
        JsonSchemaDownloader(mappings(), it).download(chart)
    }

    private fun extract(jsonSchemasDir: File, chartsDir: File, chart: Chart) = File(jsonSchemasDir, EXTRACTS_DIR).also {
        HelmDependencyExtractor(chartsDir, it).extract(chart)
    }

    private fun jsonPatch(chartFile: File, patchFile: String): JsonPatch? {
        return File(chartFile.parent, patchFile).takeIf { it.exists() }?.let {
            JsonPatch.fromJson(jsonMapper.readTree(it))
        }
    }

    private fun mappings(): Map<String, JsonSchemaRepository> {
        return JsonSchemaRepositoryMappingService.instance.list()
            .associateBy { it.name }.mapValues { it.value.toJsonSchemaRepository() }
    }

    private fun JsonSchemaRepositoryMapping.toJsonSchemaRepository(): JsonSchemaRepository {
        return JsonSchemaRepository(baseUri, username, password, valuesSchemaFile, globalValuesSchemaFile)
    }
}
