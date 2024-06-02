package io.github.fstaudt.helm.idea.settings.model

import com.intellij.credentialStore.Credentials

/**
 * Chart repository with optional JSON schema repository mapping
 */
data class ChartRepository(
    var name: String = "",
    var url: String = "",
    var referenceRepository: String = "",
    var username: String = "",
    var password: String = "",
    var pushedToHelm: Boolean = false,
    val jsonSchemaRepositoryMapping: JsonSchemaRepositoryMapping = JsonSchemaRepositoryMapping(),
) {
    fun applyFrom(item: ChartRepository) = apply {
        name = item.name
        url = item.url
        referenceRepository = item.referenceRepository
        username = item.username
        password = item.password
        pushedToHelm = item.pushedToHelm
        jsonSchemaRepositoryMapping.applyFrom(item.jsonSchemaRepositoryMapping)
    }

    fun deepCopy(): ChartRepository {
        return copy(jsonSchemaRepositoryMapping = jsonSchemaRepositoryMapping.copy())
    }

    fun referenced() = referenceRepository.isNotBlank()
    fun secured() = username.isNotBlank() && password.isNotBlank()
    fun credentials() = Credentials(username, password)
    fun pushedToHelm() = pushedToHelm
    fun toJsonSchemaRepositoryMapping(): JsonSchemaRepositoryMapping? {
        return jsonSchemaRepositoryMapping.takeIf { it.name.isNotBlank() }?.also { it.name = "@$name" }
    }
}
