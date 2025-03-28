package io.github.fstaudt.helm.idea.settings.model

import com.intellij.credentialStore.Credentials
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE

data class JsonSchemaRepositoryMapping @JvmOverloads constructor(
    var name: String = "",
    var baseUri: String = "",
    var referenceRepositoryMapping: String = "",
    var username: String = "",
    var password: String = "",
    var valuesSchemaFile: String = VALUES_SCHEMA_FILE,
) {
    fun applyFrom(item: JsonSchemaRepositoryMapping) = apply {
        name = item.name
        baseUri = item.baseUri
        referenceRepositoryMapping = item.referenceRepositoryMapping
        username = item.username
        password = item.password
        valuesSchemaFile = item.valuesSchemaFile
    }

    fun referenced() = referenceRepositoryMapping.isNotBlank()
    fun secured() = username.isNotBlank() && password.isNotBlank()
    fun credentials() = Credentials(username, password)
}
