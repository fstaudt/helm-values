package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.ChartDependency
import java.io.File

/**
 * JSON schema locator for JSON schemas stored locally
 */
interface SchemaLocator {
    /**
     * Relative path from aggregated schema of current chart to aggregated schema of chart dependency
     * @param dependency chart dependency
     */
    fun aggregatedSchemaFor(dependency: ChartDependency): String

    /**
     * Relative path from aggregated schema of current chart to schema of current chart
     */
    fun schemaFor(chartDir: File): String
}
