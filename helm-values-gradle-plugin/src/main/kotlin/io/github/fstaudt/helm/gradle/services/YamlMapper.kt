package io.github.fstaudt.helm.gradle.services

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.model.Chart
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters.None
import java.io.File

abstract class YamlMapper : BuildService<None> {
    private val objectMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun chartFrom(chartFile: Property<File>): Chart {
        return chartFile.get().inputStream().use { objectMapper.readValue(it, Chart::class.java) }
    }

    fun valuesFrom(valuesFile: Property<File>): JsonNode {
        return valuesFile.get().inputStream().use { objectMapper.readTree(it) }
    }
}
