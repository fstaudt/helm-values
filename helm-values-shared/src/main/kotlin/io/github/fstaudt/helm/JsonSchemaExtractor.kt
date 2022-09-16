package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class JsonSchemaExtractor(
    private val chartsDir: File?,
    private val extractSchemasDir: File,
) {
    companion object {
        const val EXTRACT_DIR = "extract"
        private val logger: Logger = LoggerFactory.getLogger(JsonSchemaExtractor::class.java)
    }

    fun extract(chart: Chart) {
        extractSchemasDir.deleteRecursively()
        extractSchemasDir.mkdirs()
        chart.dependencies.forEach { dependency ->
            extractSchema(dependency)
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
                            logger.info("entry ${entry?.name}: $entry")
                            while (entry != null && !entry.name.endsWith("/$HELM_SCHEMA_FILE")) {
                                logger.info("entry ${entry.name}: $entry")
                                entry = it.nextTarEntry
                            }
                            if (entry != null) {
                                with(dependency.toSchemaFileFor(entry)) {
                                    parentFile.mkdirs()
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
            logger.warn("Please run `helm dependency update .` in chart folder to download chart dependencies.")
            dependency.fallbackSchemaFor("Archive not found")
        }
    }

    private fun ChartDependency.toSchemaFileFor(entry: TarArchiveEntry): File {
        val basePath = File("$extractSchemasDir/${alias ?: name}")
        return File(basePath, entry.name.removePrefix("${name}/").replace("$HELM_CHARTS_DIR/", ""))
    }

    private fun ChartDependency.fallbackSchemaFor(errorMessage: String) {
        File("$extractSchemasDir/${alias ?: name}/$HELM_SCHEMA_FILE").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    {
                      "${'$'}schema": "$SCHEMA_VERSION",
                      "${'$'}id": "$name/$version/$HELM_SCHEMA_FILE",
                      "type": "object",
                      "title": "Fallback schema for $name:$version",
                      "description": "An error occurred during extraction from $name-$version.tgz: $errorMessage"
                    }
                """.trimIndent()
            )
        }
    }
}
