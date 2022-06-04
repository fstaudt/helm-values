package io.github.fstaudt.helm.http

import io.github.fstaudt.helm.exceptions.PublicationException
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.apache.hc.client5.http.classic.methods.HttpPut
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.ContentType.APPLICATION_JSON
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import java.io.File

class NexusRawPublisher : Publisher {
    private val client: CloseableHttpClient = HttpClientBuilder.create().useSystemProperties().build()

    override fun publish(repository: JsonSchemaRepository, chart: Chart, schemaFile: File) {
        val uri = "${repository.baseUri}/${chart.name}/${chart.version}/${schemaFile.name}"
        val request = HttpPut(uri)
        repository.basicAuthentication()?.let { request.addHeader("Authorization", it) }
        request.entity = ByteArrayEntity(schemaFile.readBytes(), APPLICATION_JSON)
        try {
            client.execute(request).use {
                if (it.code != 201)
                    throw PublicationException("${chart.name}/${chart.version}/${schemaFile.name}", it.code)
            }
        } catch (e: PublicationException) {
            throw e
        } catch (e: Exception) {
            throw PublicationException("${chart.name}/${chart.version}/${schemaFile.name}", 0, e)
        }
    }
}