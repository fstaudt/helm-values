package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.Keywords.Companion.ADDITIONAL_PROPERTIES
import io.github.fstaudt.helm.Keywords.Companion.ID
import io.github.fstaudt.helm.Keywords.Companion.SCHEMA
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.OffsetDateTime.now
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.SECONDS

@Suppress("NestedLambdaShadowedImplicitParameter")
class HelmDependencyExtractor(
    private val chartsDir: File?,
    private val extractsDir: File,
) {
    companion object {
        const val EXTRACTS_DIR = "extract"
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }
        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory
        private val logger: Logger = LoggerFactory.getLogger(HelmDependencyExtractor::class.java)
    }

    fun extract(chart: Chart) {
        extractsDir.deleteRecursively()
        extractsDir.mkdirs()
        chart.dependencies.filter { it.version != null }.forEach { extractFrom(it) }
    }

    private fun extractFrom(dependency: ChartDependency) {
        val archive = File(chartsDir, "${dependency.name}-${dependency.version}.tgz")
        if (archive.exists()) {
            try {
                archive.inputStream().use {
                    GzipCompressorInputStream(it).use {
                        TarArchiveInputStream(it).use {
                            var entry: TarArchiveEntry? = it.nextTarEntry
                            while (entry != null) {
                                logger.debug("entry {}: {}", entry.name, entry)
                                if (entry.name.endsWith("/$HELM_CHART_FILE")
                                    || entry.name.endsWith("/$HELM_VALUES_FILE")
                                    || entry.name.endsWith("/$HELM_SCHEMA_FILE")
                                ) {
                                    with(entry.toFile()) {
                                        parentFile.mkdirs()
                                        writeBytes(it.readAllBytes())
                                    }
                                }
                                entry = it.nextTarEntry
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("${dependency.name}:${dependency.version}: archive invalid - skipping dependency.")
                File(extractsDir, dependency.name).deleteRecursively()
                dependency.writeFallbackSchemaFor("${e.javaClass.simpleName} - ${e.localizedMessage}")
            }
        } else {
            logger.warn("${dependency.name}:${dependency.version}: archive not found - skipping dependency.")
            logger.warn("Please run `helm dependency update .` in chart folder to download chart dependencies.")
            dependency.writeFallbackSchemaFor("Archive not found")
        }
    }

    private fun TarArchiveEntry.toFile() = File(extractsDir, name.replace("$HELM_CHARTS_DIR/", ""))

    private fun ChartDependency.writeFallbackSchemaFor(errorMessage: String) {
        File("$extractsDir/$name/$HELM_SCHEMA_FILE").also {
            it.parentFile.mkdirs()
            val errorLabel = "An error occurred during extraction from archive $HELM_CHARTS_DIR/$name-$version.tgz"
            val fallbackSchema = ObjectNode(nodeFactory)
                .put(SCHEMA, SCHEMA_VERSION)
                .put(ID, "$name/$version/$HELM_SCHEMA_FILE")
                .put("x-generated-by", GENERATOR_LABEL)
                .put("x-generated-at", "${now(UTC).truncatedTo(SECONDS)}")
                .put("title", "Fallback schema for $name:$version")
                .put("description", "$NEW_LINE $errorLabel: '$errorMessage'")
                .put("x-intellij-html-description", "<br>$errorLabel:<br> <code>$errorMessage</code>")
                .put("type", "object")
                .put(ADDITIONAL_PROPERTIES, false)
            jsonMapper.writeValue(it, fallbackSchema)
        }
    }
}
