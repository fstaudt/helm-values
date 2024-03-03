package io.github.fstaudt.helm.idea.settings.model

data class ChartRepositorySetting(
    var url: String = "",
    var referenceRepository: String = "",
    var synchronized: Boolean = true
)
