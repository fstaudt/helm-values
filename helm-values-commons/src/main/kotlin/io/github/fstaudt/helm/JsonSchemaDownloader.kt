package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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
    private val downloadSchemasDir: File
) {

    companion object {
        private val FULL_URI_REGEX = Regex("http(s)?://.*")
        private val URI_FILENAME_REGEX = Regex("/[^/]*$")
    }

    private val logger: Logger = LoggerFactory.getLogger(JsonSchemaDownloader::class.java)

    private val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(SerializationFeature.INDENT_OUTPUT)
    }
    private val client: CloseableHttpClient = HttpClientBuilder.create().useSystemProperties().build()

    fun download(chart: Chart) {
        downloadSchemasDir.deleteRecursively()
        downloadSchemasDir.mkdirs()
        chart.dependencies.forEach { dependency ->
            repositoryMappings[dependency.repository]?.let {
                downloadSchema(dependency, it, it.valuesSchemaFile)
                downloadSchema(dependency, it, it.globalValuesSchemaFile)
            }
        }
    }

    private fun downloadSchema(dependency: ChartDependency, repository: JsonSchemaRepository, fileName: String) {
        val uri = URI("${repository.baseUri}/${dependency.name}/${dependency.version}/$fileName")
        val downloadFolder = File(downloadSchemasDir, dependency.aliasOrName())
        downloadSchema(dependency, uri, DownloadedSchema(downloadFolder, fileName, false), repository)
    }

    private fun downloadSchema(
        dependency: ChartDependency,
        uri: URI,
        downloadedSchema: DownloadedSchema,
        repository: JsonSchemaRepository?,
    ) {
        if (!downloadedSchema.file().exists()) {
            logger.info("Downloading $downloadedSchema from $uri")
            val request = HttpGet(uri)
            repository?.basicAuthentication()?.let { request.addHeader("Authorization", it) }
            request.toResponseBody(dependency).let {
                downloadedSchema.file().parentFile.mkdirs()
                downloadedSchema.file().writeText(it)
            }
            downloadSchemaReferences(dependency, uri, downloadedSchema)
        }
    }

    private fun downloadSchemaReferences(dependency: ChartDependency, uri: URI, downloadedSchema: DownloadedSchema) {
        val jsonSchema = jsonMapper.readTree(downloadedSchema.file())
        val needsRewrite = jsonSchema.findValues("\$ref").any {
            it.isFullUri() || (!downloadedSchema.isReference && !it.isSimpleFile())
        }
        jsonSchema.findParents("\$ref").map {
            with(it.get("\$ref")) {
                if (!isLocalReference()) {
                    val refUri = referenceUri(uri)
                    val refDownloadedSchema = refDownloadedSchema(refUri, downloadedSchema)
                    val refRepository = repositoryMappings
                        .filterValues { "$refUri".startsWith(it.baseUri) }.values
                        .firstOrNull()
                    downloadSchema(dependency, refUri, refDownloadedSchema, refRepository)
                    if (isFullUri() || (!downloadedSchema.isReference && !isSimpleFile())) {
                        (it as ObjectNode).replace("\$ref", TextNode(refUri.toDownloadedUri()))
                    }
                }
            }
        }
        if (needsRewrite) jsonMapper.writeValue(downloadedSchema.file(), jsonSchema)
    }

    private fun JsonNode.referenceUri(uri: URI) = when {
        isFullUri() -> URI(textValue())
        else -> URI("$uri".replace(URI_FILENAME_REGEX, "/${textValue()}")).normalize()
    }

    private fun JsonNode.refDownloadedSchema(refUri: URI, downloadedSchema: DownloadedSchema) = when {
        isSimpleFile() -> {
            val refPath = downloadedSchema.path.replace(URI_FILENAME_REGEX, "/${textValue()}")
            DownloadedSchema(downloadedSchema.baseFolder, refPath, downloadedSchema.isReference)
        }
        else -> DownloadedSchema(downloadedSchema.baseFolder, refUri.path, true)
    }

    private fun HttpGet.toResponseBody(dependency: ChartDependency): String {
        return try {
            client.execute(this).use {
                if (it.code == 200)
                    EntityUtils.toString(it.entity)
                else
                    fallbackSchemaFor(dependency, "${it.code} - ${it.reasonPhrase}")
            }
        } catch (e: Exception) {
            fallbackSchemaFor(dependency, "${e.javaClass.simpleName} - ${e.localizedMessage}")
        }
    }

    private fun HttpGet.fallbackSchemaFor(dependency: ChartDependency, errorMessage: String): String {
        return """
            {
              "${'$'}schema": "$SCHEMA_VERSION",
              "${'$'}id": "$uri",
              "type": "object",
              "title": "Fallback schema for ${dependency.repository}/${dependency.name}:${dependency.version}",
              "description":"An error occurred during download of $uri: $errorMessage"
            }
            """.trimIndent()
    }

    private fun JsonNode.isLocalReference() = textValue().startsWith("#")
    private fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)
    private fun JsonNode.isSimpleFile() = !textValue().contains("/")
    private fun URI.toDownloadedUri() = "${path.removePrefix("/")}${fragment?.let { "#$it" } ?: ""}"
}
