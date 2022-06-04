package io.github.fstaudt.helm.model

import java.io.Serializable

/**
 * JSON schemas repository for validation and documentation of values in charts of a Helm repository.
 *
 * JSON schemas repository must be structured with one folder per chart and one sub-folder per version.
 * ```
 * repository
 *  |-- my-chart
 *       |-- 0.1.0
 *            |-- values.schema.json          // JSON schema for values.yaml
 *            |-- global-values.schema.json   // JSON schema for global section in values.yaml
 * ```
 *
 * @param baseUri base URI to the repository
 *
 *                 must include protocol and host, may include port and path and should not end with "/"
 *                 examples: http://my.charts.repository:1080, https://my.charts.repository/apps
 * @param username optional username for basic authentication
 * @param password optional password for basic authentication
 * @param valuesSchemaFile optional file name of JSON schema for values.yaml (defaults to values.schema.json)
 * @param globalValuesSchemaFile optional file name of JSON schema for global section in values.yaml (defaults to global-values.schema.json)
 */
data class JsonSchemaRepository(
    val baseUri: String,
    val username: String? = null,
    val password: String? = null,
    val valuesSchemaFile: String = VALUES_SCHEMA_FILE,
    val globalValuesSchemaFile: String = GLOBAL_VALUES_SCHEMA_FILE,
) : Serializable {
    companion object {
        const val VALUES_SCHEMA_FILE = "values.schema.json"
        const val GLOBAL_VALUES_SCHEMA_FILE = "global-values.schema.json"
    }
}
