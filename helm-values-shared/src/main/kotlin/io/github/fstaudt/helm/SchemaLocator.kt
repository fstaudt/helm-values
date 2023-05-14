package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

/**
 * JSON schema locator for JSON schemas stored locally
 */
interface SchemaLocator {
    /**
     * Aggregated schema of chart dependency
     * @param dependency chart dependency
     */
    fun aggregatedSchemaFor(dependency: ChartDependency): File
}
