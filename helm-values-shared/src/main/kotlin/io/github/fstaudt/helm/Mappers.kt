package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.model.ChartDependencyImport

object Mappers {
    val chartMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.registerModule(SimpleModule().apply {
            addDeserializer(ChartDependencyImport::class.java, ChartDependencyImport.Deserializer())
        })
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    val chartMetadataMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
