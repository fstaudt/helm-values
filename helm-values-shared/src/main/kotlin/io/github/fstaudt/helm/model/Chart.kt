package io.github.fstaudt.helm.model

data class Chart(
    var apiVersion: String,
    var name: String,
    var version: String,
    var dependencies: List<ChartDependency> = emptyList(),
)
