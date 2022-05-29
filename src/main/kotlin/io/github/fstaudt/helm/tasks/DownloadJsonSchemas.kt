package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.RepositoryMapping
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

@UntrackedTask(because = "depends on external JSON schema repositories")
open class DownloadJsonSchemas : DefaultTask() {
    companion object {
        const val DOWNLOAD_JSON_SCHEMAS = "downloadJsonSchemas"
        const val DOWNLOADS = "$HELM_VALUES/downloads"
        private val FULL_URI_REGEX = Regex("http(s)?://.*")
        private val URI_FILENAME_REGEX = Regex("/[^/]*$")
    }

    private val logger: Logger = LoggerFactory.getLogger(DownloadJsonSchemas::class.java)

    @OutputDirectory
    val downloadedSchemasFolder = File(project.buildDir, DOWNLOADS)

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @InputFile
    @PathSensitive(RELATIVE)
    lateinit var chartFile: File

    private val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
    }
    private val client: CloseableHttpClient = HttpClientBuilder.create().useSystemProperties().build()

    @TaskAction
    fun download() {
        downloadedSchemasFolder.deleteRecursively()
        downloadedSchemasFolder.mkdirs()
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        chart.dependencies.forEach { dependency ->
            downloadSchema(dependency, "helm-values.json")
            downloadSchema(dependency, "helm-global.json")
        }
    }

    private fun downloadSchema(dependency: ChartDependency, fileName: String) {
        extension.repositoryMappings[dependency.repository]?.let {
            val uri = URI("${it.basePath}/${dependency.name}/${dependency.version}/$fileName")
            val downloadFolder = File(downloadedSchemasFolder, it.downloadFolder)
            downloadSchema(uri, downloadFolder, it)
        }
    }

    private fun downloadSchema(uri: URI, downloadFolder: File, repository: RepositoryMapping?) {
        val downloadedSchema = File(downloadFolder, uri.path)
        if (!downloadedSchema.exists()) {
            logger.info("Downloading $downloadedSchema from $uri")
            val request = HttpGet(uri)
            repository?.authorizationHeader?.let { request.addHeader("Authorization", it) }
            request.toResponseBody().let {
                downloadedSchema.ensureParentDirsCreated()
                downloadedSchema.writeText(it)
                downloadSchemaReferences(downloadFolder, downloadedSchema, uri)
            }
        }
    }

    private fun downloadSchemaReferences(
        downloadFolder: File,
        downloadedSchema: File,
        uri: URI
    ) {
        val jsonSchema = jsonMapper.readTree(downloadedSchema)
        val containsFullUri = jsonSchema.findParents("\$ref").map {
            val ref = it.get("\$ref")
            val fullRefUri = ref.isFullUri()
            if (!ref.isLocalReference()) {
                val refUri = when {
                    ref.isFullUri() -> URI(ref.textValue())
                    else -> URI("$uri".replace(URI_FILENAME_REGEX, "/${ref.textValue()}")).normalize()
                }
                val refDownloadFolder = when {
                    ref.isFullUri() -> File(downloadedSchema.parentFile, "refs")
                    else -> downloadFolder
                }
                val refRepository = extension.repositoryMappings
                    .filterValues { "$refUri".startsWith(it.basePath) }.values
                    .firstOrNull()
                downloadSchema(refUri, refDownloadFolder, refRepository)
                if (ref.isFullUri()) {
                    (it as ObjectNode).replace("\$ref", TextNode(refUri.toRelativeUri()))
                }
            }
            fullRefUri
        }.any { it }
        if (containsFullUri) jsonMapper.writeValue(downloadedSchema, jsonSchema)
    }

    private fun HttpGet.toResponseBody(): String {
        return try {
            client.execute(this).use {
                if (it.code == 200)
                    EntityUtils.toString(it.entity)
                else
                    errorSchemaFor("${it.code} - ${it.reasonPhrase}")
            }
        } catch (e: Exception) {
            errorSchemaFor("${e.javaClass.simpleName} - ${e.localizedMessage}")
        }
    }

    private fun HttpGet.errorSchemaFor(errorMessage: String): String {
        return """
                    {
                      "${'$'}schema":"$SCHEMA_VERSION",
                      "${'$'}id":"$uri",
                      "type":"object",
                      "${'$'}error":"$errorMessage"
                    }
                """.trimIndent()
    }

    private fun JsonNode.isLocalReference() = textValue().startsWith("#")
    private fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)
    private fun URI.toRelativeUri() = "refs${path}${fragment?.let { "#$it" } ?: ""}"
}



