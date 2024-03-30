package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.services.GradleSchemaLocator
import io.github.fstaudt.helm.gradle.services.JsonMapper
import io.github.fstaudt.helm.gradle.services.YamlMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class AggregateJsonSchema : DefaultTask() {
    companion object {
        const val AGGREGATE_JSON_SCHEMA = "aggregateJsonSchema"
    }

    @get:Nested
    abstract var extension: HelmValuesExtension

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    abstract val chartFile: Property<File>

    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    abstract val patchAggregatedFile: Property<File>

    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    abstract val patchValuesFile: Property<File>

    @InputDirectory
    @PathSensitive(RELATIVE)
    val downloadSchemasDir: Provider<Directory> = layout.buildDirectory.dir("$HELM_VALUES/$DOWNLOADS_DIR")

    @InputDirectory
    @PathSensitive(RELATIVE)
    val extractSchemasDir: Provider<Directory> = layout.buildDirectory.dir("$HELM_VALUES/$EXTRACTS_DIR")

    @OutputFile
    val aggregatedSchemaFile: Provider<RegularFile> = layout.buildDirectory.file("$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Internal
    abstract val yamlMapper: Property<YamlMapper>

    @get:Internal
    abstract val jsonMapper: Property<JsonMapper>

    @TaskAction
    fun aggregate() {
        val aggregator = JsonSchemaAggregator(
            extension.repositoryMappings,
            GradleSchemaLocator(layout.projectDirectory.asFile),
            chartFile.get().parentFile,
            downloadSchemasDir.get().asFile,
            extractSchemasDir.get().asFile)
        val chart = yamlMapper.get().chartFrom(chartFile)
        val valuesJsonPatch = jsonMapper.get().patchFrom(patchValuesFile)
        val aggregatedJsonPatch = jsonMapper.get().patchFrom(patchAggregatedFile)
        aggregator.aggregate(chart, valuesJsonPatch, aggregatedJsonPatch).also {
            jsonMapper.get().writeTo(aggregatedSchemaFile, it)
        }
    }
}
