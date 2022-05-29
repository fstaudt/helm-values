package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.RepositoryMapping
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

open class HelmValuesAssistantExtension {
    companion object {
        const val EXTENSION = "helmValuesAssistant"
        const val HELM_SOURCES_DIRECTORY = "."
    }

    @Input
    var sourcesDirectory: String = HELM_SOURCES_DIRECTORY

    @Input
    var repositoryMappings: Map<String, RepositoryMapping> = emptyMap()

    @Input
    @Optional
    var targetRepository: String? = null
}
