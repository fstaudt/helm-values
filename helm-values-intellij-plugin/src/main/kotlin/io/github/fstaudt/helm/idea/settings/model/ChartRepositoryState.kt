package io.github.fstaudt.helm.idea.settings.model

data class ChartRepositoryState(
    var url: String = "",
    var referenceRepository: String = "",
    var pushedToHelm: Boolean = true
)
