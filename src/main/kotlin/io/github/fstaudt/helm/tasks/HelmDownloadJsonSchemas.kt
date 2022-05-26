package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.HelmValuesAssistantExtension
import io.github.fstaudt.helm.HelmValuesAssistantPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
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
import java.io.File

@UntrackedTask(because = "depends on external JSON schema repositories")
open class HelmDownloadJsonSchemas : DefaultTask() {
    companion object {
        const val HELM_DOWNLOAD_JSON_SCHEMAS = "helmDownloadJsonSchemas"
        const val DOWNLOADS = "downloads"
    }

    @OutputDirectory
    val downloadedSchemasFolder = File(project.buildDir, "$HELM_VALUES/$DOWNLOADS")

    @Nested
    lateinit var extension: HelmValuesAssistantExtension

    @InputFile
    lateinit var chartFile: File

    private val mapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val client: CloseableHttpClient = HttpClientBuilder.create().useSystemProperties().build()

    @TaskAction
    fun download() {
        downloadedSchemasFolder.deleteRecursively()
        val chart = chartFile.inputStream().use { mapper.readValue(it, Chart::class.java) }
        chart.dependencies.forEach { dependency ->
            downloadSchema(dependency, "helm-values.json")
            downloadSchema(dependency, "helm-global.json")
        }
    }

    private fun downloadSchema(dependency: ChartDependency, fileName: String) {
        val valuesSchemaFile = File(downloadedSchemasFolder, "${dependency.alias ?: dependency.name}/$fileName")
        valuesSchemaFile.ensureParentDirsCreated()
        extension.repositoryMappings[dependency.repository]?.let { repository ->
            val url = "${repository.basePath}/${dependency.name}/${dependency.version}/$fileName"
            jsonSchema(url, repository.authorizationHeader).let {
                valuesSchemaFile.writeText(it)
            }
        } ?: valuesSchemaFile.writeText("{}")
    }

    private fun jsonSchema(url: String, authorizationHeader: String?): String {
        val request = HttpGet(url)
        authorizationHeader?.let { request.addHeader("Authorization", authorizationHeader) }
        return client.execute(request).use {
            if (it.code == 200) EntityUtils.toString(it.entity) else "{\"errorCode\":\"${it.code}\"}"
        }
    }
}