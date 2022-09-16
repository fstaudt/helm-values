package io.github.fstaudt.helm.model

import java.io.File

data class DownloadedSchema(val baseFolder: File, val path: String, val isReference: Boolean) {
    fun file() = File(baseFolder, path)
}
