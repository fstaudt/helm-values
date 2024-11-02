package io.github.fstaudt.helm.idea

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

object Mappers {
    val metadataMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.registerModule(JavaTimeModule())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        it.configure(WRITE_DATES_AS_TIMESTAMPS, false)
    }
}
