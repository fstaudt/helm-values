package io.github.fstaudt.helm.idea.settings.model

import io.github.fstaudt.helm.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE

data class JsonSchemaRepository @JvmOverloads constructor(
    var baseUri: String = "",
    var valuesSchemaFile: String = VALUES_SCHEMA_FILE,
    var globalValuesSchemaFile: String = GLOBAL_VALUES_SCHEMA_FILE,
)