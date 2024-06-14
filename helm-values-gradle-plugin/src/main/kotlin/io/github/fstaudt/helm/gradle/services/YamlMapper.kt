package io.github.fstaudt.helm.gradle.services

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.Mappers.chartMapper
import io.github.fstaudt.helm.model.Chart
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters.None
import java.io.File

abstract class YamlMapper : BuildService<None> {
    fun chartFrom(chartFile: Property<File>): Chart {
        return chartFile.get().inputStream().use { chartMapper.readValue(it, Chart::class.java) }
    }

    fun valuesFrom(valuesFile: Property<File>): JsonNode {
        return valuesFile.get().inputStream().use { chartMapper.readTree(it) }
    }

    fun patchFrom(yamlFile: Property<File>): JsonPatch? {
        return yamlFile.get().takeIf { it.exists() }?.toJsonPatch()
    }

    private fun File.toJsonPatch() = let { JsonPatch.fromJson(chartMapper.readTree(it)) }
}
