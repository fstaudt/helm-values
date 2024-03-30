package io.github.fstaudt.helm.idea.settings.model

import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_DEPRECATION
import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE

data class JsonSchemaRepository @JvmOverloads constructor(
    var baseUri: String = "",
    var referenceRepositoryMapping: String = "",
    var valuesSchemaFile: String = VALUES_SCHEMA_FILE,
    @Deprecated(GLOBAL_VALUES_DEPRECATION)
    var globalValuesSchemaFile: String = GLOBAL_VALUES_SCHEMA_FILE,
)
