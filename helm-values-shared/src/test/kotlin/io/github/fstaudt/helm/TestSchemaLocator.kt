package io.github.fstaudt.helm

import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

class TestSchemaLocator(private val testProject: TestProject) : SchemaLocator {
    override fun aggregatedSchemaFor(dependency: ChartDependency): File {
        return File(testProject, "${dependency.localPath()}/$AGGREGATED_SCHEMA_FILE")
    }
}
