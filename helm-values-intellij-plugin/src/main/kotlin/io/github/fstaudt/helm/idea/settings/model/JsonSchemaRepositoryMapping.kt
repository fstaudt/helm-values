package io.github.fstaudt.helm.idea.settings.model

import io.github.fstaudt.helm.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE

data class JsonSchemaRepositoryMapping @JvmOverloads constructor(
    var name: String = "",
    var baseUri: String = "",
    var username: String = "",
    var password: String = "",
    var valuesSchemaFile: String = VALUES_SCHEMA_FILE,
    var globalValuesSchemaFile: String = GLOBAL_VALUES_SCHEMA_FILE,
) {
    fun applyFrom(item: JsonSchemaRepositoryMapping) {
        name = item.name
        baseUri = item.baseUri
        username = item.username
        password = item.password
        valuesSchemaFile = item.valuesSchemaFile
        globalValuesSchemaFile = item.globalValuesSchemaFile
    }

    fun secured() = username.isNotBlank() && password.isNotBlank()
}
