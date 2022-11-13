package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule

class ObjectNodeExtensions {
    companion object {
        private val jsonMapper = ObjectMapper().also {
            it.registerModule(KotlinModule.Builder().build())
            it.enable(SerializationFeature.INDENT_OUTPUT)
        }

        private val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory

        fun ObjectNode.objectNode(name: String): ObjectNode {
            return get(name) as? ObjectNode ?: ObjectNode(nodeFactory).also { set<ObjectNode>(name, it) }
        }

        fun ObjectNode.properties() = objectNode("properties")

        fun ObjectNode.global() = objectNode("global")

        fun ObjectNode.allOf(): ArrayNode {
            return get("allOf") as? ArrayNode ?: ArrayNode(nodeFactory).also { set<ObjectNode>("allOf", it) }
        }
    }
}
