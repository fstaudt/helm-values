package io.github.fstaudt.helm.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF

data class RefMapping(val baseUri: String, val mappedBaseUri: String) {

    companion object {
        fun ObjectNode.updateReferencesFor(refMappings: List<RefMapping>) {
            findParents(REF).forEach { parent ->
                val ref = parent[REF]
                refMappings.firstOrNull { it.matches(ref) }?.let {
                    (parent as ObjectNode).replace(REF, it.map(ref))
                }
            }
        }
    }

    fun matches(ref: JsonNode) = ref.textValue().startsWith(baseUri)
    fun map(ref: JsonNode): JsonNode {
        val textValue = if (mappedBaseUri.contains("#") && !baseUri.contains("#")) {
            ref.textValue().replaceFirst("#", "")
        } else {
            ref.textValue()
        }
        return TextNode(textValue.replaceFirst(baseUri, mappedBaseUri))
    }
}

