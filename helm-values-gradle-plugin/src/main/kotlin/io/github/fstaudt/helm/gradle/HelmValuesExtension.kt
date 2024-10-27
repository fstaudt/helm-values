package io.github.fstaudt.helm.gradle

import io.github.fstaudt.helm.gradle.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import io.github.fstaudt.helm.gradle.tasks.PublishJsonSchemas.Companion.PUBLISH_JSON_SCHEMAS
import io.github.fstaudt.helm.model.JsonSchemaRepository
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface HelmValuesExtension {
    companion object {
        const val EXTENSION = "helmValues"
        const val HELM_SOURCES_DIR = "."
    }

    /**
     * Base directory for sources of Helm chart, containing at least Chart.yaml.
     *
     * Default to project base directory.
     */
    @get:Input
    val sourcesDir: Property<String>

    /**
     * Mappings between Helm repository and repository hosting JSON schemas for charts
     *
     * Keys relate to repository in dependencies of Helm chart.
     */
    @get:Input
    val repositoryMappings: MapProperty<String, JsonSchemaRepository>

    /**
     * Key to JSON schemas repository in repositoryMappings for JSON schemas publication
     *
     * Mandatory for tasks [GENERATE_JSON_SCHEMAS] & [PUBLISH_JSON_SCHEMAS]
     */
    @get:Input
    @get:Optional
    val publicationRepository: Property<String>

    /**
     * Version for JSON schemas publication (overwrites default version in Chart.yaml)
     */
    @get:Input
    @get:Optional
    val publishedVersion: Property<String>
}
