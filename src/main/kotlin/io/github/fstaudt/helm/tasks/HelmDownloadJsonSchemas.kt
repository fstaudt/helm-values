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
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

@UntrackedTask(because = "depends on external JSON schema repositories")
open class HelmDownloadJsonSchemas : DefaultTask() {
    companion object {
        const val SCHEMA_VERSION = "https://json-schema.org/draft/2020-12/schema"
        const val HELM_DOWNLOAD_JSON_SCHEMAS = "helmDownloadJsonSchemas"
        const val DOWNLOADS = "$HELM_VALUES/downloads"
        private val FULL_URI_REGEX = Regex("http(s)?://.*")
        private val URI_FILENAME_REGEX = Regex("/[^/]*$")
        private val DEFAULT_REPOSITORY = RepositoryMapping("default", "")
    }

    private val logger: Logger = LoggerFactory.getLogger(HelmDownloadJsonSchemas::class.java)

    @OutputDirectory
    val downloadedSchemasFolder = File(project.buildDir, DOWNLOADS)

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @InputFile
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
        val chart = chartFile.inputStream().use { yamlMapper.readValue(it, Chart::class.java) }
        chart.dependencies.forEach { dependency ->
            downloadSchema(dependency, "helm-values.json")
            downloadSchema(dependency, "helm-global.json")
        }
    }

    private fun downloadSchema(dependency: ChartDependency, fileName: String) {
        extension.repositoryMappings.firstOrNull { it.id == dependency.repository }?.let {
            val uri = URI("${it.basePath}/${dependency.name}/${dependency.version}/$fileName")
            val downloadedSchema = File(downloadedSchemasFolder, "${it.id.toFolderName()}${uri.path}")
            downloadSchema(uri, downloadedSchema, it)
        }
    }

    private fun downloadSchema(uri: URI, downloadedSchema: File, repository: RepositoryMapping) {
        if (!downloadedSchema.exists()) {
            logger.info("Downloading $downloadedSchema from $uri")
            val request = HttpGet(uri)
            repository.authorizationHeader?.let { request.addHeader("Authorization", it) }
            request.toResponseBody().let {
                downloadedSchema.ensureParentDirsCreated()
                downloadedSchema.writeText(it)
                downloadSchemaReferences(downloadedSchema, uri, repository)
            }
        }
    }

    private fun downloadSchemaReferences(downloadedSchema: File, uri: URI, repository: RepositoryMapping) {
        val jsonSchema = jsonMapper.readTree(downloadedSchema)
        val containsFullUri = jsonSchema.findParents("\$ref").map {
            val ref = it.get("\$ref")
            val fullRefUri = ref.isFullUri()
            if (!ref.isLocalReference()) {
                val refUri = when {
                    ref.isFullUri() -> URI(ref.textValue())
                    else -> URI("$uri".replace(URI_FILENAME_REGEX, "/${ref.textValue()}")).normalize()
                }
                val refDownloadedSchema = when {
                    ref.isFullUri() -> File(downloadedSchema.parentFile, "refs${refUri.path}")
                    else -> File(downloadedSchemasFolder, "${repository.id.toFolderName()}${refUri.path}")
                }
                val refRepository = when {
                    ref.isFullUri() -> extension.repositoryMappings.firstOrNull { "$refUri".startsWith(it.basePath) }
                    else -> repository
                } ?: DEFAULT_REPOSITORY
                downloadSchema(refUri, refDownloadedSchema, refRepository)
                if (ref.isFullUri()) {
                    (it as ObjectNode).replace("\$ref", TextNode(refUri.toRelativeUri()))
                }
            }
            fullRefUri
        }.any { it }
        if (containsFullUri) jsonMapper.writeValue(downloadedSchema, jsonSchema)
    }

    private fun HttpGet.toResponseBody(): String {
        return client.execute(this).use {
            if (it.code == 200)
                EntityUtils.toString(it.entity)
            else
                """
                    {
                      "${'$'}schema":"$SCHEMA_VERSION",
                      "${'$'}id":"$uri",
                      "type":"object",
                      "${'$'}error":"${it.code} - ${it.reasonPhrase}"
                    }
                """.trimIndent()
        }
    }

    private fun JsonNode.isLocalReference() = textValue().startsWith("#")
    private fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)
    private fun URI.toRelativeUri() = "refs${path}${fragment?.let { "#$it" } ?: ""}"
    private fun String.toFolderName() = replace(Regex("[^a-zA-Z\\d]"), "").toLowerCase()
}



