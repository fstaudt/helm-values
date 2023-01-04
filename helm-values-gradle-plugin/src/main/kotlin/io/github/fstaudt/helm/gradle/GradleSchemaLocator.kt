package io.github.fstaudt.helm.gradle

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class GradleSchemaLocator(private val sourcesDir: String) : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency): String {
        return "../../${dependency.localPath()}/build/$HELM_VALUES/$AGGREGATED_SCHEMA_FILE"
    }

    override fun schemaFor(chartDir: File) = "../../$sourcesDir/$HELM_SCHEMA_FILE"
}
