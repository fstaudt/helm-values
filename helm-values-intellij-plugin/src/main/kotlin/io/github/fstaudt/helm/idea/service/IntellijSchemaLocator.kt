package io.github.fstaudt.helm.idea.service

import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.SchemaLocator
import io.github.fstaudt.helm.model.ChartDependency

class IntellijSchemaLocator : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency): String {
        return "../${dependency.localPath()?.substringAfterLast("/")}/$AGGREGATED_SCHEMA_FILE"
    }
}
