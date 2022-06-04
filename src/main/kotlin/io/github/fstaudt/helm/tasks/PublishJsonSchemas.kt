package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.http.Publisher
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATED
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

@UntrackedTask(because = "depends on external JSON schema repositories")
open class PublishJsonSchemas : DefaultTask() {
    companion object {
        const val PUBLISH_JSON_SCHEMAS = "publishJsonSchemas"
    }

    @InputDirectory
    @PathSensitive(RELATIVE)
    val generatedSchemaDir = File(project.buildDir, GENERATED)

    @InputFile
    @PathSensitive(RELATIVE)
    lateinit var chartFile: File

    @Internal
    lateinit var publisher: Publisher

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @TaskAction
    fun publish() {
        val repository = extension.publicationRepository()
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        extension.publishedVersion?.let { chart.version = it }
        publisher.publish(repository, chart, File(generatedSchemaDir, repository.valuesSchemaFile))
        publisher.publish(repository, chart, File(generatedSchemaDir, repository.globalValuesSchemaFile))
    }
}