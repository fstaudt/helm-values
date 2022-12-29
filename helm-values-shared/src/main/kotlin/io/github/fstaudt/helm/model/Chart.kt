package io.github.fstaudt.helm.model

data class Chart(
    val apiVersion: String,
    val name: String,
    var version: String,
    val dependencies: List<ChartDependency> = emptyList(),
)
