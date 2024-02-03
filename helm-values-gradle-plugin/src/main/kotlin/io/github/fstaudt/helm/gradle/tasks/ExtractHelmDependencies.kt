package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.HelmDependencyExtractor
import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.services.YamlMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class ExtractHelmDependencies : DefaultTask() {
    companion object {
        const val EXTRACT_HELM_DEPENDENCIES = "extractHelmDependencies"
    }

    @get:Nested
    abstract var extension: HelmValuesExtension

    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    abstract val chartsDir: Property<File>

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    abstract val chartFile: Property<File>

    @OutputDirectory
    val extractSchemasDir: Provider<Directory> = layout.buildDirectory.dir("$HELM_VALUES/$EXTRACTS_DIR")

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Internal
    abstract val yamlMapper: Property<YamlMapper>

    @TaskAction
    fun extract() {
        val chart = yamlMapper.get().chartFrom(chartFile)
        val extractor = HelmDependencyExtractor(chartsDir.orNull, extractSchemasDir.get().asFile)
        extractor.extract(chart)
    }
}
