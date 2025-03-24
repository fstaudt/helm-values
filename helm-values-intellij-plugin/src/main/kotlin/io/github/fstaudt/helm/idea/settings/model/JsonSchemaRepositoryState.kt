package io.github.fstaudt.helm.idea.settings.model

import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE

data class JsonSchemaRepositoryState @JvmOverloads constructor(
    var baseUri: String = "",
    var referenceRepositoryMapping: String = "",
    var valuesSchemaFile: String = VALUES_SCHEMA_FILE,
)
