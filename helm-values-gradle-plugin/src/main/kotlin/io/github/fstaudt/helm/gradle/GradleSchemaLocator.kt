package io.github.fstaudt.helm.gradle

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.model.ChartDependency

class GradleSchemaLocator : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency): String {
        return "../../${dependency.localPath()}/build/$HELM_VALUES/$AGGREGATED_SCHEMA_FILE"
    }
}
