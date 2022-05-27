package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.RepositoryMapping
import org.gradle.api.tasks.Input

open class HelmValuesAssistantExtension {
    companion object {
        val EXTENSION = "helmValuesAssistant"
        const val HELM_SOURCES_DIRECTORY = "."
    }

    @Input
    var sourcesDirectory: String = HELM_SOURCES_DIRECTORY

    @Input
    var repositoryMappings: List<RepositoryMapping> = emptyList()
}
