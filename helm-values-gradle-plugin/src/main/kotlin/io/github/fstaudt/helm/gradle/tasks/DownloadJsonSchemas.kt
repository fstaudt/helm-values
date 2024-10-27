package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaDownloader
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.services.YamlMapper
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class DownloadJsonSchemas : DefaultTask() {
    companion object {
        const val DOWNLOAD_JSON_SCHEMAS = "downloadJsonSchemas"
    }

    @get:Input
    abstract val repositoryMappings: MapProperty<String, JsonSchemaRepository>

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    abstract val chartFile: Property<File>

    @OutputDirectory
    val downloadSchemasDir: Provider<RegularFile> = layout.buildDirectory.file("$HELM_VALUES/$DOWNLOADS_DIR")

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Internal
    abstract val yamlMapper: Property<YamlMapper>

    @TaskAction
    fun download() {
        val chart = yamlMapper.get().chartFrom(chartFile)
        val downloader = JsonSchemaDownloader(repositoryMappings.get(), downloadSchemasDir.get().asFile)
        downloader.download(chart)
    }
}

