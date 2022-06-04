package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.tasks.GenerateJsonSchemas.Companion.GENERATE_JSON_SCHEMAS
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option

open class HelmValuesAssistantExtension {
    companion object {
        const val EXTENSION = "helmValuesAssistant"
        const val HELM_SOURCES_DIR = "."
    }

    /**
     * Base directory for sources of Helm chart, containing at least Chart.yaml.
     *
     * Default to project base directory.
     */
    @Input
    @Option(description = "Base directory for sources of Helm chart, containing at least Chart.yaml (default to project base directory)")
    var sourcesDir: String = HELM_SOURCES_DIR

    /**
     * Mappings between Helm repository and repository hosting JSON schemas for charts
     *
     * Keys relate to repository in dependencies of Helm chart.
     */
    @Input
    var repositoryMappings: Map<String, JsonSchemaRepository> = emptyMap()

    /**
     * repository for JSON schemas publication
     *
     * mandatory for task [GENERATE_JSON_SCHEMAS]
     */
    @Input
    @Optional
    var publicationRepository: String? = null

    /**
     * Optional version for JSON schemas publication (overwrites default version in Chart.yaml)
     */
    @Input
    @Optional
    var publishedVersion: String? = null
}
