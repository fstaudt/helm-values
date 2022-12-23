package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class JsonSchemaExtractor(
    private val chartsDir: File?,
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val extractSchemasDir: File,
) {
    companion object {
        const val EXTRACT_DIR = "extract"
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }
        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory
        private val logger: Logger = LoggerFactory.getLogger(JsonSchemaExtractor::class.java)
    }

    fun extract(chart: Chart) {
        extractSchemasDir.deleteRecursively()
        extractSchemasDir.mkdirs()
        chart.dependencies.filter { it.version != null && !it.isStoredLocally() }.forEach { dependency ->
            if (!repositoryMappings.contains(dependency.repository)) {
                extractSchema(dependency)
            }
        }
    }

    private fun extractSchema(dependency: ChartDependency) {
        val archive = File(chartsDir, "${dependency.name}-${dependency.version}.tgz")
        if (archive.exists()) {
            try {
                archive.inputStream().use {
                    GzipCompressorInputStream(it).use {
                        TarArchiveInputStream(it).use {
                            var entry: TarArchiveEntry? = it.nextTarEntry
                            while (entry != null) {
                                logger.info("entry ${entry.name}: $entry")
                                if (entry.name.endsWith("/$HELM_SCHEMA_FILE")) {
                                    with(dependency.toSchemaFileFor(entry)) {
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
                dependency.fallbackSchemaFor("${e.javaClass.simpleName} - ${e.localizedMessage}")
            }
        } else {
            logger.warn("${dependency.name}:${dependency.version}: archive not found - skipping dependency.")
            logger.warn("Please run `helm dependency update .` in chart folder to download chart dependencies.")
            dependency.fallbackSchemaFor("Archive not found")
        }
    }

    private fun ChartDependency.toSchemaFileFor(entry: TarArchiveEntry): File {
        val basePath = File("$extractSchemasDir/${aliasOrName()}")
        return File(basePath, entry.name.removePrefix("${name}/").replace("$HELM_CHARTS_DIR/", ""))
    }

    private fun ChartDependency.fallbackSchemaFor(errorMessage: String) {
        File("$extractSchemasDir/${alias ?: name}/$HELM_SCHEMA_FILE").also {
            it.parentFile.mkdirs()
            val errorLabel = "An error occurred during extraction from archive $HELM_CHARTS_DIR/$name-$version.tgz"
            val fallbackSchema = ObjectNode(nodeFactory)
                .put("\$schema", SCHEMA_VERSION)
                .put("\$id", "$name/$version/$HELM_SCHEMA_FILE")
                .put("type", "object")
                .put("additionalProperties", false)
                .put("title", "Fallback schema for $name:$version")
                .put("description", "$NEW_LINE $errorLabel: '$errorMessage'")
                .put("x-intellij-html-description", "<br>$errorLabel:<br> <code>$errorMessage</code>")
            jsonMapper.writeValue(it, fallbackSchema)
        }
    }
}
