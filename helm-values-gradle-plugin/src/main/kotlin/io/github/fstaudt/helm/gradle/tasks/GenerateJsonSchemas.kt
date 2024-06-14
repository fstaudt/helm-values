package io.github.fstaudt.helm.gradle.tasks

import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaGenerator
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GENERATION_DIR
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.services.JsonMapper
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
abstract class GenerateJsonSchemas : DefaultTask() {
    companion object {
        const val GENERATE_JSON_SCHEMAS = "generateJsonSchemas"
    }

    @get:Nested
    abstract var extension: HelmValuesExtension

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    abstract val chartFile: Property<File>

    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    abstract val valuesPatchFile: Property<File>

    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    abstract val valuesYamlPatchFile: Property<File>

    @OutputDirectory
    val generatedSchemaDir: Provider<Directory> = layout.buildDirectory.dir("$HELM_VALUES/$GENERATION_DIR")

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Internal
    abstract val yamlMapper: Property<YamlMapper>

    @get:Internal
    abstract val jsonMapper: Property<JsonMapper>

    @TaskAction
    fun generate() {
        val chart = yamlMapper.get().chartFrom(chartFile)
        extension.publishedVersion?.let { publishedVersion ->
            chart.version = publishedVersion
            chart.dependencies.filter { it.isStoredLocally() }.forEach {
                it.apply { version = publishedVersion }
            }
        }
        val repository = extension.publicationRepository()
        val generator = JsonSchemaGenerator(extension.repositoryMappings, extension.publicationRepository)
        val jsonPatch = jsonPatch()
        val jsonSchema = generator.generateValuesJsonSchema(chart, jsonPatch)
        jsonMapper.get().writeTo(generatedSchemaDir.map { it.file(repository.valuesSchemaFile) }, jsonSchema)
    }

    private fun jsonPatch(): JsonPatch? {
        return jsonMapper.get().patchFrom(valuesPatchFile) ?: yamlMapper.get().patchFrom(valuesYamlPatchFile)
    }
}
