package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.ChartDependency

class TestSchemaLocator : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency) = "${dependency.localPath()}/$AGGREGATED_SCHEMA_FILE"
}
