package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class TestSchemaLocator : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency) = "${dependency.localPath()}/$AGGREGATED_SCHEMA_FILE"

    override fun schemaFor(chartDir: File) = "${chartDir.name}/$HELM_SCHEMA_FILE"
}
