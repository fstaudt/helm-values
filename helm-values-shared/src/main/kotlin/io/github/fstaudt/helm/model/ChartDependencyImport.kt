package io.github.fstaudt.helm.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken.END_OBJECT
import com.fasterxml.jackson.core.JsonToken.START_OBJECT
import com.fasterxml.jackson.core.JsonToken.VALUE_STRING
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

data class ChartDependencyImport(val child: String, val parent: String) {

    private data class Builder(var child: String? = null, var parent: String? = null) {
        fun build() = ChartDependencyImport(child!!, parent!!)
    }

    class Deserializer : JsonDeserializer<ChartDependencyImport>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ChartDependencyImport {
            return when (p.currentToken()) {
                VALUE_STRING -> p.valueAsString.let { ChartDependencyImport("exports.$it", it) }
                START_OBJECT -> {
                    Builder().apply {
                        while (p.nextToken() != END_OBJECT) {
                            when (p.currentName) {
                                ChartDependencyImport::child.name -> p.nextToken().also { child = p.valueAsString }
                                ChartDependencyImport::parent.name -> p.nextToken().also { parent = p.valueAsString }
                            }
                        }
                    }.build()
                }

                else -> throw IllegalStateException("Unexpected token: ${p.currentToken}")
            }
        }
    }
}
