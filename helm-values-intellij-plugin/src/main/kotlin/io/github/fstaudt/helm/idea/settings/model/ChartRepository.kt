package io.github.fstaudt.helm.idea.settings.model

import com.intellij.credentialStore.Credentials

data class ChartRepository(
    var name: String = "",
    var url: String = "",
    var referenceRepository: String = "",
    var username: String = "",
    var password: String = "",
    var synchronized: Boolean = false,
) {
    fun applyFrom(item: ChartRepository) {
        name = item.name
        url = item.url
        username = item.username
        password = item.password
    }

    fun referenced() = referenceRepository.isNotBlank()
    fun secured() = username.isNotBlank() && password.isNotBlank()
    fun credentials() = Credentials(username, password)
    fun synchronized() = synchronized
}
