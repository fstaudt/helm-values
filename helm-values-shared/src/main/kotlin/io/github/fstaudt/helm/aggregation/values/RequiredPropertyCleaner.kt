package io.github.fstaudt.helm.aggregation.values

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.allOfOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.objectNodeOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.propsOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.refOrNull
import io.github.fstaudt.helm.ObjectNodeExtensions.requiredOrNull
import io.github.fstaudt.helm.model.Chart
import java.io.File

/**
 * Discard required properties when property is already set in dependency values
 */
class RequiredPropertyCleaner(extractsDir: File) {

    private val extractedValuesAggregator = ExtractedValuesAggregator(extractsDir)
    fun discardRequiredPropertiesFor(chart: Chart, jsonSchema: ObjectNode) {
        val values = extractedValuesAggregator.aggregate(chart)
        jsonSchema.discardRequiredPropertiesSetIn(values, jsonSchema)
    }

    private fun ObjectNode.discardRequiredPropertiesSetIn(values: JsonNode, jsonSchema: ObjectNode) {
        allOfOrNull()?.forEach {
            (it as? ObjectNode)?.discardRequiredPropertiesSetIn(values, jsonSchema)
        }
        refOrNull()?.let { ref ->
            val pointer = ref.textValue().removePrefix("#")
            runCatching { jsonSchema.at(pointer) as? ObjectNode }.getOrNull()?.run {
                discardRequiredPropertiesSetIn(values, jsonSchema)
            }
            return
        }
        requiredOrNull()?.removeAll { it.textValue() in values.fieldNames().asSequence() }
        propsOrNull()?.run {
            values.fieldNames().forEach { value ->
                objectNodeOrNull(value)?.run {
                    discardRequiredPropertiesSetIn(values.get(value), jsonSchema)
                }
            }
        }
    }
}

