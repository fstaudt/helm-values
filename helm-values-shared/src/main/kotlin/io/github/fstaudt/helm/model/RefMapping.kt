package io.github.fstaudt.helm.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode

data class RefMapping(val baseUri: String, val mappedBaseUri: String) {
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

