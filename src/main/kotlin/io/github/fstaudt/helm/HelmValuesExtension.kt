package io.github.fstaudt.helm

import io.github.fstaudt.helm.exceptions.RepositoryMappingException
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.tasks.PublishJsonSchemas.Companion.PUBLISH_JSON_SCHEMAS
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

open class HelmValuesExtension {
    companion object {
        const val EXTENSION = "helmValues"
        const val HELM_SOURCES_DIR = "."
    }

    /**
     * Base directory for sources of Helm chart, containing at least Chart.yaml.
     *
     * Default to project base directory.
     */
    @Input
    var sourcesDir: String = HELM_SOURCES_DIR

    /**
     * Mappings between Helm repository and repository hosting JSON schemas for charts
     *
     * Keys relate to repository in dependencies of Helm chart.
     */
    @Input
    var repositoryMappings: Map<String, JsonSchemaRepository> = emptyMap()

    /**
     * Key to JSON schemas repository in repositoryMappings for JSON schemas publication
     *
     * Mandatory for tasks [GENERATE_JSON_SCHEMAS] & [PUBLISH_JSON_SCHEMAS]
     */
    @Input
    @Optional
    var publicationRepository: String? = null

    /**
     * Version for JSON schemas publication (overwrites default version in Chart.yaml)
     */
    @Input
    @Optional
    var publishedVersion: String? = null

    fun publicationRepository(): JsonSchemaRepository {
        return repositoryMappings[publicationRepository] ?: throw RepositoryMappingException(publicationRepository)
    }
}