package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
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

        internal fun ObjectNode.objectNode(name: String): ObjectNode {
            return get(name) as? ObjectNode ?: ObjectNode(nodeFactory).also { set<ObjectNode>(name, it) }
        }

        internal fun ObjectNode.properties() = objectNode("properties")

        internal fun ObjectNode.global() = objectNode("global")

        internal fun ObjectNode.allOf(): ArrayNode {
            return get("allOf") as? ArrayNode ?: ArrayNode(nodeFactory).also { set<ArrayNode>("allOf", it) }
        }

        internal fun JsonNode.isInternalReference() = textValue().startsWith("#")

        internal fun JsonNode.toUriFrom(uri: URI) = when {
            isFullUri() -> URI(textValue())
            else -> URI("$uri".replace(URI_FILENAME_REGEX, textValue())).normalize()
        }

        internal fun JsonNode.isFullUri() = textValue().matches(FULL_URI_REGEX)

        internal fun File.toObjectNode(): ObjectNode {
            return runCatching { jsonMapper.readTree(this) as ObjectNode }.getOrDefault(nodeFactory.objectNode())
        }
    }
}
