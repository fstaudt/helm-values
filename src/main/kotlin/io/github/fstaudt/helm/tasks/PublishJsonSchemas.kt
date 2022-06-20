package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesExtension
import io.github.fstaudt.helm.http.JsonSchemaPublisher
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATED
import org.gradle.api.DefaultTask
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

@UntrackedTask(because = "depends on external JSON schema repositories")
open class PublishJsonSchemas : DefaultTask() {
    companion object {
        const val PUBLISH_JSON_SCHEMAS = "publishJsonSchemas"
    }

    @Nested
    lateinit var extension: HelmValuesExtension

    @InputFile
    @SkipWhenEmpty
    @PathSensitive(RELATIVE)
    var chartFile: File? = null

    @InputDirectory
    @PathSensitive(RELATIVE)
    val generatedSchemaDir = File(project.buildDir, GENERATED)

    @Internal
    lateinit var jsonSchemaPublisher: JsonSchemaPublisher

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @TaskAction
    fun publish() {
        val repository = extension.publicationRepository()
        val chart = chartFile?.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        extension.publishedVersion?.let { chart.version = it }
        jsonSchemaPublisher.publish(repository, chart, File(generatedSchemaDir, repository.valuesSchemaFile))
        jsonSchemaPublisher.publish(repository, chart, File(generatedSchemaDir, repository.globalValuesSchemaFile))
    }
}