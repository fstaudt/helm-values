package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.DownloadedSchema
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

@Suppress("NestedLambdaShadowedImplicitParameter")
class JsonSchemaDownloader(
    private val repositoryMappings: Map<String, JsonSchemaRepository>,
    private val downloadSchemasDir: File,
) {

    companion object {
        const val DOWNLOADS_DIR = "downloads"
        private val FULL_URI_REGEX = Regex("http(s)?://.*")
        private val URI_FILENAME_REGEX = Regex("[^/]+$")
        private val logger: Logger = LoggerFactory.getLogger(JsonSchemaDownloader::class.java)
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }
        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory
        private val client: CloseableHttpClient = HttpClientBuilder.create().useSystemProperties().build()
    }

    fun download(chart: Chart) {
        downloadSchemasDir.mkdirs()
        chart.dependencies.filter { it.version != null }.forEach { dependency ->
            repositoryMappings[dependency.repository]?.let {
                downloadSchema(dependency, it, it.valuesSchemaFile)
            }
        }
    }

    private fun downloadSchema(dependency: ChartDependency, repository: JsonSchemaRepository, fileName: String) {
        val uri = URI("${repository.baseUri}/${dependency.name}/${dependency.version}/$fileName")
        val downloadFolder = File(downloadSchemasDir, dependency.aliasOrName())
        downloadSchema(dependency.fullName(), uri, DownloadedSchema(downloadFolder, fileName, false), repository)
    }

    private fun downloadSchema(
        schemaName: String,
        uri: URI,
        downloadedSchema: DownloadedSchema,
        repository: JsonSchemaRepository?
    ) {
        if (!downloadedSchema.file().exists()) {
            logger.info("Downloading $downloadedSchema from $uri")
            val request = HttpGet(uri)
            repository?.basicAuthentication()?.let { request.addHeader("Authorization", it) }
            request.toResponseBody(schemaName).let {
                downloadedSchema.file().parentFile.mkdirs()
                downloadedSchema.file().writeText(it)
            }
            downloadSchemaReferences(uri, downloadedSchema)
        }
    }

    private fun downloadSchemaReferences(uri: URI, downloadedSchema: DownloadedSchema) {
        val jsonSchema = jsonMapper.readTree(downloadedSchema.file())
        val needsRewrite = jsonSchema.findValues("\$ref").any {
            it.isFullUri() || (!downloadedSchema.isReference && !it.isSimpleFile())
        }
        jsonSchema.findParents("\$ref").map {
            val ref = it.get("\$ref")
            if (!ref.isInternalReference()) {
                try {
                    val refUri = ref.toUriFrom(uri)
                    val refDownloadedSchema = ref.toDownloadedSchemaFrom(refUri, downloadedSchema)
                    val refRepository =
                        repositoryMappings.filterValues { "$refUri".startsWith(it.baseUri) }.values.firstOrNull()
                    downloadSchema(refUri.path, refUri, refDownloadedSchema, refRepository)
                    if (ref.isFullUri() || (!downloadedSchema.isReference && !ref.isSimpleFile())) {
                        (it as ObjectNode).replace("\$ref", TextNode(refUri.toDownloadedUri()))
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to download schema for ref \"${ref.textValue()}\"", e)
                }
            }
        }
        if (needsRewrite) jsonMapper.writeValue(downloadedSchema.file(), jsonSchema)
    }

    private fun JsonNode.toUriFrom(uri: URI) = when {
        isFullUri() -> URI(textValue())
        else -> URI("$uri".replace(URI_FILENAME_REGEX, textValue())).normalize()
    }

    private fun JsonNode.toDownloadedSchemaFrom(refUri: URI, downloadedSchema: DownloadedSchema) = when {
        isSimpleFile() -> {
            val refPath = downloadedSchema.path.replace(URI_FILENAME_REGEX, textValue())
            DownloadedSchema(downloadedSchema.baseFolder, refPath, downloadedSchema.isReference)
        }

        else -> DownloadedSchema(downloadedSchema.baseFolder, refUri.path, true)
    }

    private fun HttpGet.toResponseBody(schemaName: String): String {
        return try {
            client.execute(this).use {
                if (it.code == 200) {
                    EntityUtils.toString(it.entity)
                } else {
                    fallbackSchemaFor(schemaName, "HTTP ${it.code} - ${it.reasonPhrase}")
                }
            }
        } catch (e: Exception) {
            fallbackSchemaFor(schemaName, "${e.javaClass.simpleName} - ${e.localizedMessage}")
        }
    }

    private fun HttpGet.fallbackSchemaFor(schemaName: String, errorMessage: String): String {
        val errorLabel = "An error occurred during download of $uri:"
        val htmlLabel = "An error occurred during download of <a href='$uri'>JSON schema for $schemaName</a>:"
        return ObjectNode(nodeFactory)
            .put("\$schema", SCHEMA_VERSION)
            .put("\$id", "$uri")
            .put("type", "object")
            .put("additionalProperties", false)
            .put("title", "Fallback schema for $schemaName")
            .put("description", "$NEW_LINE $errorLabel '$errorMessage'")
            .put("x-intellij-html-description", "<br>$htmlLabel<br><code>$errorMessage</code>")
            .toPrettyString()
    }

    private fun JsonNode.isInternalReference() = textValue().startsWith("#")
    private fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)
    private fun JsonNode.isSimpleFile() = !textValue().contains("/")
    private fun URI.toDownloadedUri() = "${path.removePrefix("/")}${fragment?.let { "#$it" } ?: ""}"
}
