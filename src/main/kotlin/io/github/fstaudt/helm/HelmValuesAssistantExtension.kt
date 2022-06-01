package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.RepositoryMapping
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import io.github.fstaudt.helm.tasks.GenerateJsonSchema

open class HelmValuesAssistantExtension {
    companion object {
        const val EXTENSION = "helmValuesAssistant"
        const val HELM_SOURCES_DIRECTORY = "."
    }

    @Input
    var sourcesDirectory: String = HELM_SOURCES_DIRECTORY

    @Input
    var repositoryMappings: Map<String, RepositoryMapping> = emptyMap()

    /**
     * repository for JSON schemas publication
     *
     * mandatory for task [GenerateJsonSchema.GENERATE_JSON_SCHEMA]
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
