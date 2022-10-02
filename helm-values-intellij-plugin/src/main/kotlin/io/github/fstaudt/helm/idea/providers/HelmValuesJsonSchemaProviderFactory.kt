package io.github.fstaudt.helm.idea.providers

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import io.github.fstaudt.helm.idea.helmChartDirs

class HelmValuesJsonSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        if (project.isDisposed) {
            return emptyList()
        }
        return project.helmChartDirs().map { HelmValuesJsonSchemaProvider(project, it) }
    }
}