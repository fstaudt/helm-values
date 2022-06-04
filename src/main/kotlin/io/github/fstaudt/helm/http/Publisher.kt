package io.github.fstaudt.helm.http

import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.JsonSchemaRepository
import java.io.File

interface Publisher {

    /**
     * Publish JSON schema file to a JSON schema repository
     * @param repository the JSON schema repository where the file must be published
     * @param chart the chart
     * @param schemaFile the JSON schema file to publish
     */
    fun publish(repository: JsonSchemaRepository, chart: Chart, schemaFile: File)
}