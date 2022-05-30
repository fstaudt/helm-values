package io.github.fstaudt.helm.model

data class ChartDependency(
        val name: String,
        val version: String,
        val repository: String,
        val alias: String? = null,
        val condition: String? = null,
) {
        fun aliasOrName() = alias ?: name
        fun fullName() = "$repository/$name:$version"
}
