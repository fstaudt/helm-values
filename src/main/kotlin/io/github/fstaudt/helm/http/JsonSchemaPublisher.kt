package io.github.fstaudt.helm.http

import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.io.File

interface JsonSchemaPublisher {

    /**
     * Publish JSON schema file to a JSON schema repository
     * @param repository the JSON schema repository where the file must be published
     * @param chart the chart
     * @param schemaFile a JSON schema file for the chart
     */
    fun publish(repository: JsonSchemaRepository, chart: Chart, schemaFile: File)
}