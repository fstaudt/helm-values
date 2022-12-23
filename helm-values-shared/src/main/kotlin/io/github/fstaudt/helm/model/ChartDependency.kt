package io.github.fstaudt.helm.model

data class ChartDependency(
    val name: String,
    val version: String? = null,
    val repository: String? = null,
    val alias: String? = null,
    val condition: String? = null,
) {
    companion object {
        private const val LOCAL_PATH_PREFIX = "file://"
    }

    fun aliasOrName() = alias ?: name
    fun isStoredLocally() = repository?.startsWith(LOCAL_PATH_PREFIX) == true
    fun localPath() = repository?.takeIf { isStoredLocally() }?.removePrefix(LOCAL_PATH_PREFIX)
}
