package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
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
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

@CacheableTask
@Suppress("NestedLambdaShadowedImplicitParameter")
open class UnpackJsonSchemas : DefaultTask() {
    companion object {
        const val UNPACK_JSON_SCHEMAS = "unpackJsonSchemas"
        const val CHARTS_DIR = "charts"
        const val UNPACK = "unpack"
        const val HELM_SCHEMA_FILE = "values.schema.json"
    }

    private val logger: Logger = LoggerFactory.getLogger(UnpackJsonSchemas::class.java)

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @InputDirectory
    @Optional
    @PathSensitive(RELATIVE)
    var chartsDir: File? = null

    @InputFile
    @SkipWhenEmpty
    @PathSensitive(RELATIVE)
    var chartFile: File? = null

    @OutputDirectory
    val unpackSchemasDir = File(project.buildDir, "$HELM_VALUES/$UNPACK")

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @TaskAction
    fun download() {
        unpackSchemasDir.deleteRecursively()
        unpackSchemasDir.mkdirs()
        val chart = chartFile?.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        chart.dependencies.forEach { dependency ->
            unpackSchema(dependency)
        }
    }

    private fun unpackSchema(dependency: ChartDependency) {
        val archive = File(chartsDir, "${dependency.name}-${dependency.version}.tgz")
        if (archive.exists()) {
            try {
                archive.inputStream().use {
                    GzipCompressorInputStream(it).use {
                        TarArchiveInputStream(it).use {
                            var entry: TarArchiveEntry? = it.nextTarEntry
                            logger.info("entry ${entry?.name}: $entry")
                            while (entry != null && !entry.name.endsWith("/$HELM_SCHEMA_FILE")) {
                                logger.info("entry ${entry.name}: $entry")
                                entry = it.nextTarEntry
                            }
                            if (entry != null) {
                                with(dependency.toSchemaFileFor(entry)) {
                                    ensureParentDirsCreated()
                                    writeBytes(it.readAllBytes())
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                dependency.fallbackSchemaFor("${e.javaClass.simpleName} - ${e.localizedMessage}")
            }
        } else {
            logger.warn("${dependency.name}:${dependency.version}: archive not found - skipping dependency.")
            logger.warn("Please run `helm dependency update ${extension.sourcesDir}` to download dependencies in charts folder.")
            dependency.fallbackSchemaFor("Archive not found")
        }
    }

    private fun ChartDependency.toSchemaFileFor(entry: TarArchiveEntry): File {
        val basePath = File("$unpackSchemasDir/${alias ?: name}")
        return File(basePath, entry.name.removePrefix("${name}/").replace("charts/", ""))
    }

    private fun ChartDependency.fallbackSchemaFor(errorMessage: String) {
        File("$unpackSchemasDir/${alias ?: name}/$HELM_SCHEMA_FILE").also {
            it.ensureParentDirsCreated()
            it.writeText(
                """
                    {
                      "${'$'}schema": "$SCHEMA_VERSION",
                      "${'$'}id": "$name/$version/$HELM_SCHEMA_FILE",
                      "type": "object",
                      "title": "Fallback schema for $name:$version",
                      "description": "An error occurred during unpack of $name-$version.tgz: $errorMessage"
                    }
                """.trimIndent()
            )
        }
    }
}