package io.github.fstaudt.helm.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ChartDependency(
    val name: String,
    var version: String? = null,
    val repository: String? = null,
    val alias: String? = null,
    val condition: String? = null,
    @JsonProperty("import-values") val importValues: List<ChartDependencyImport> = emptyList()
) {
    companion object {
        private const val LOCAL_PATH_PREFIX = "file://"
    }

    fun sanitizedVersion() = version?.filter { "$it".matches(Regex("[\\dA-Za-z.\\-+]")) }
    fun aliasOrName() = alias ?: name
    fun isStoredLocally() = repository?.startsWith(LOCAL_PATH_PREFIX) == true
    fun localPath() = repository?.takeIf { isStoredLocally() }?.removePrefix(LOCAL_PATH_PREFIX)
}
