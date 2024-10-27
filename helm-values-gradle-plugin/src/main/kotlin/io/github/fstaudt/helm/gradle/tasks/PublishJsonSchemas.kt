package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GENERATION_DIR
import io.github.fstaudt.helm.exceptions.RepositoryMappingException
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.services.YamlMapper
import io.github.fstaudt.helm.http.NexusRawJsonSchemaPublisher
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
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

    @get:Input
    @get:Optional
    abstract val publishedVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val publicationRepository: Property<String>

    @get:Input
    abstract val repositoryMappings: MapProperty<String, JsonSchemaRepository>

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
        val repository = publicationRepository()
        val chart = yamlMapper.get().chartFrom(chartFile)
        publishedVersion.orNull?.let { chart.version = it }
        jsonSchemaPublisher.publish(repository, chart,
            File(generatedSchemaDir.get().asFile, repository.valuesSchemaFile))
    }

    private fun publicationRepository(): JsonSchemaRepository {
        val jsonSchemaRepository = repositoryMappings.get()[publicationRepository.orNull]
        return jsonSchemaRepository ?: throw RepositoryMappingException(publicationRepository.orNull)
    }
}
