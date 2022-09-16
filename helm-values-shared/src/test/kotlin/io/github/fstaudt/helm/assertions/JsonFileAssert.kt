package io.github.fstaudt.helm.assertions

import net.javacrumbs.jsonunit.assertj.JsonAssert
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.assertj.core.api.AbstractFileAssert
import org.assertj.core.internal.Files.instance
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.Charset
import java.nio.file.Files

class JsonFileAssert(actual: File) : AbstractFileAssert<JsonFileAssert>(actual, JsonFileAssert::class.java) {
    private var files = instance()

    companion object {
        fun assertThatJsonFile(path: String) = JsonFileAssert(File(path))
    }

    fun hasContent(charset: Charset = Charset.defaultCharset()): JsonAssert {
        files.assertCanRead(info, actual)
        val fileContent = readFile(charset)
        return assertThatJson(fileContent)
    }

    private fun readFile(charset: Charset): String {
        return try {
            String(Files.readAllBytes(actual.toPath()), charset)
        } catch (e: IOException) {
            throw UncheckedIOException(String.format("Failed to read %s content with %s charset", actual, charset), e)
        }
    }
}
