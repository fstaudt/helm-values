package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.SCHEMA_VERSION
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.apache.commons.codec.binary.Base64
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
@Suppress("NestedLambdaShadowedImplicitParameter", "UnstableApiUsage")
open class DownloadJsonSchemas : DefaultTask() {
    companion object {
        const val DOWNLOAD_JSON_SCHEMAS = "downloadJsonSchemas"
        const val DOWNLOADS = "$HELM_VALUES/downloads"
        private val FULL_URI_REGEX = Regex("http(s)?://.*")
        private val URI_FILENAME_REGEX = Regex("/[^/]*$")
    }

    private data class DownloadedSchema(val baseFolder: File, val path: String, val isReference: Boolean) {
        fun file() = File(baseFolder, path)
    }

    private val logger: Logger = LoggerFactory.getLogger(DownloadJsonSchemas::class.java)

    @OutputDirectory
    val downloadedSchemasDir = File(project.buildDir, DOWNLOADS)

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
        it.enable(INDENT_OUTPUT)
    }
    private val client: CloseableHttpClient = HttpClientBuilder.create().useSystemProperties().build()

    @TaskAction
    fun download() {
        downloadedSchemasDir.deleteRecursively()
        downloadedSchemasDir.mkdirs()
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        chart.dependencies.forEach { dependency ->
            downloadSchema(dependency, "helm-values.json")
            downloadSchema(dependency, "helm-global.json")
        }
    }

    private fun downloadSchema(dependency: ChartDependency, fileName: String) {
        extension.repositoryMappings[dependency.repository]?.let {
            val uri = URI("${it.baseUri}/${dependency.name}/${dependency.version}/$fileName")
            val downloadFolder = File(downloadedSchemasDir, dependency.aliasOrName())
            downloadSchema(uri, DownloadedSchema(downloadFolder, fileName, false), it)
        }
    }

    private fun downloadSchema(uri: URI, downloadedSchema: DownloadedSchema, repository: JsonSchemaRepository?) {
        if (!downloadedSchema.file().exists()) {
            logger.info("Downloading $downloadedSchema from $uri")
            val request = HttpGet(uri)
            repository?.username?.let { request.addHeader("Authorization", basic(it, repository.password)) }
            request.toResponseBody().let {
                downloadedSchema.file().ensureParentDirsCreated()
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
            with(it.get("\$ref")) {
                if (!isLocalReference()) {
                    val refUri = when {
                        isFullUri() -> URI(textValue())
                        else -> URI("$uri".replace(URI_FILENAME_REGEX, "/${textValue()}")).normalize()
                    }
                    val refRepository = extension.repositoryMappings
                        .filterValues { "$refUri".startsWith(it.baseUri) }.values
                        .firstOrNull()
                    val refDownloadedSchema = when {
                        isSimpleFile() -> {
                            val refPath = downloadedSchema.path.replace(URI_FILENAME_REGEX, "/${textValue()}")
                            DownloadedSchema(downloadedSchema.baseFolder, refPath, downloadedSchema.isReference)
                        }
                        else -> DownloadedSchema(downloadedSchema.baseFolder, refUri.path, true)
                    }
                    downloadSchema(refUri, refDownloadedSchema, refRepository)
                    if (isFullUri() || (!downloadedSchema.isReference && !isSimpleFile())) {
                        (it as ObjectNode).replace("\$ref", TextNode(refUri.toDownloadedUri()))
                    }
                }
            }
        }
        if (needsRewrite) jsonMapper.writeValue(downloadedSchema.file(), jsonSchema)
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
                      "${'$'}schema": "$SCHEMA_VERSION",
                      "${'$'}id": "$uri",
                      "type": "object",
                      "title": "Error schema for $uri",
                      "description":"An error occurred during download of $uri: $errorMessage"
                    }
                """.trimIndent()
    }

    private fun JsonNode.isLocalReference() = textValue().startsWith("#")
    private fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)
    private fun JsonNode.isSimpleFile() = !textValue().contains("/")
    private fun URI.toDownloadedUri() = "${path.removePrefix("/")}${fragment?.let { "#$it" } ?: ""}"

    private fun basic(username: String, password: String?): String {
        return "Basic ${Base64.encodeBase64String("$username:$password".toByteArray())}"
    }
}

