package io.github.fstaudt.helm.model

data class ChartDependency(
        val name: String,
        val version: String,
        val repository: String = "",
        val alias: String? = null,
)
