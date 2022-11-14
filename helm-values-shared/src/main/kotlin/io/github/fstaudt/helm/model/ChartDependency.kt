package io.github.fstaudt.helm.model

data class ChartDependency(
    val name: String,
    val version: String? = null,
    val repository: String? = null,
    val alias: String? = null,
    val condition: String? = null,
) {
    fun aliasOrName() = alias ?: name
    fun fullName() = "${repository?.let { "$it/" } ?: ""}$name${version?.let { ":$it" } ?: ""}"
}
