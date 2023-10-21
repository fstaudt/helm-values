package io.github.fstaudt.helm.gradle.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmDependencyExtractor
import io.github.fstaudt.helm.HelmDependencyExtractor.Companion.EXTRACTS_DIR
import io.github.fstaudt.helm.gradle.HelmValuesExtension
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.model.Chart
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
open class ExtractJsonSchemas : DefaultTask() {
    companion object {
        const val EXTRACT_JSON_SCHEMAS = "extractJsonSchemas"
    }

    @Nested
    lateinit var extension: HelmValuesExtension

    @InputDirectory
    @Optional
    @PathSensitive(RELATIVE)
    var chartsDir: File? = null

    @InputFile
    @SkipWhenEmpty
    @PathSensitive(RELATIVE)
    var chartFile: File? = null

    @OutputDirectory
    val extractSchemasDir = File(project.buildDir, "$HELM_VALUES/$EXTRACTS_DIR")

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @TaskAction
    fun extract() {
        val chart = chartFile?.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        val extractor = HelmDependencyExtractor(chartsDir, extractSchemasDir)
        extractor.extract(chart)
    }
}
