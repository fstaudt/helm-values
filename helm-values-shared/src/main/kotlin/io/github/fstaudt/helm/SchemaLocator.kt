package io.github.fstaudt.helm

import io.github.fstaudt.helm.model.ChartDependency

/**
 * JSON schema locator for chart dependencies stored locally
 */
interface SchemaLocator {
    /**
     * Path to aggregated schema of chart dependency
     * @param dependency chart dependency
     */
    fun aggregatedSchemaFor(dependency: ChartDependency): String
}
