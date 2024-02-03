package io.github.fstaudt.helm.gradle.services

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class GradleSchemaLocator(private val projectDir: File) : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency): File {
        val dependencyDir = File(projectDir, dependency.localPath()!!)
        return File(dependencyDir, "build/$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")
    }
}
