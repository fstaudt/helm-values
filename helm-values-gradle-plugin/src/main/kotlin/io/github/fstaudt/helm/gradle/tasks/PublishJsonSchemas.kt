package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GENERATION_DIR
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.services.YamlMapper
import io.github.fstaudt.helm.http.NexusRawJsonSchemaPublisher
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import javax.inject.Inject

@UntrackedTask(because = "depends on external JSON schema repositories")
abstract class PublishJsonSchemas : DefaultTask() {
    companion object {
        const val PUBLISH_JSON_SCHEMAS = "publishJsonSchemas"
    }

    @get:Nested
    abstract var extension: HelmValuesExtension

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    abstract val chartFile: Property<File>

    @InputDirectory
    @PathSensitive(RELATIVE)
    val generatedSchemaDir: Provider<RegularFile> = layout.buildDirectory.file("$HELM_VALUES/$GENERATION_DIR")

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Internal
    abstract val yamlMapper: Property<YamlMapper>

    @TaskAction
    fun publish() {
        val jsonSchemaPublisher = NexusRawJsonSchemaPublisher()
        val repository = extension.publicationRepository()
        val chart = yamlMapper.get().chartFrom(chartFile)
        extension.publishedVersion?.let { chart.version = it }
        jsonSchemaPublisher.publish(repository, chart,
            File(generatedSchemaDir.get().asFile, repository.valuesSchemaFile))
    }
}
