package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.fstaudt.helm.Keywords.Companion.ALL_OF
import io.github.fstaudt.helm.Keywords.Companion.GLOBAL
import io.github.fstaudt.helm.Keywords.Companion.PROPERTIES
import io.github.fstaudt.helm.Keywords.Companion.REQUIRED
import io.github.fstaudt.helm.Keywords.Companion.REF
import java.io.File
import java.net.URI

class ObjectNodeExtensions {
    companion object {
        private val FULL_URI_REGEX = Regex("http(s)?://.*")
        private val URI_FILENAME_REGEX = Regex("[^/]+(#.*)?$")

        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }

        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory

        internal fun ObjectNode.objectNodeOrNull(name: String) = get(name) as? ObjectNode

        internal fun ObjectNode.objectNode(name: String): ObjectNode {
            return objectNodeOrNull(name) ?: ObjectNode(nodeFactory).also { set<ObjectNode>(name, it) }
        }

        internal fun ObjectNode.props() = objectNode(PROPERTIES)
        internal fun ObjectNode.propsOrNull() = objectNodeOrNull(PROPERTIES)

        internal fun ObjectNode.global() = objectNode(GLOBAL)
        internal fun ObjectNode.globalOrNull() = objectNodeOrNull(GLOBAL)

        internal fun ObjectNode.allOfOrNull(): ArrayNode? = get(ALL_OF) as? ArrayNode

        internal fun ObjectNode.allOf(): ArrayNode {
            return allOfOrNull() ?: ArrayNode(nodeFactory).also { set<ArrayNode>(ALL_OF, it) }
        }

        internal fun ObjectNode.requiredOrNull(): ArrayNode? = get(REQUIRED) as? ArrayNode
        internal fun ObjectNode.refOrNull(): TextNode? = get(REF) as? TextNode

        internal fun JsonNode.isInternalReference() = textValue().startsWith("#")

        internal fun JsonNode.toUriFrom(uri: URI) = when {
            isFullUri() -> URI(textValue())
            else -> URI("$uri".replace(URI_FILENAME_REGEX, textValue())).normalize()
        }

        internal fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)

        internal fun File.toObjectNode(): ObjectNode {
            return runCatching { jsonMapper.readTree(this) as ObjectNode }.getOrDefault(nodeFactory.objectNode())
        }

        internal fun ObjectNode.removeAdditionalAndUnevaluatedProperties() {
            remove(Keywords.ADDITIONAL_PROPERTIES)
            remove(Keywords.UNEVALUATED_PROPERTIES)
            propsOrNull()?.globalOrNull()?.let {
                it.remove(Keywords.ADDITIONAL_PROPERTIES)
                it.remove(Keywords.UNEVALUATED_PROPERTIES)
            }
        }
    }
}
