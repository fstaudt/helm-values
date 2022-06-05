package io.github.fstaudt.helm.tasks

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal

@Suppress("NestedLambdaShadowedImplicitParameter")
abstract class JsonSchemaGenerationTask : DefaultTask() {
    companion object {
        const val EMPTY = "\n\\n "
    }

    @Internal
    protected val yamlMapper = ObjectMapper(YAMLFactory()).also {
        it.registerModule(KotlinModule.Builder().build())
        it.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Internal
    protected val jsonMapper = ObjectMapper().also {
        it.registerModule(KotlinModule.Builder().build())
        it.enable(INDENT_OUTPUT)
    }

    @Internal
    protected val nodeFactory: JsonNodeFactory = jsonMapper.nodeFactory

    protected fun ObjectNode.objectNode(propertyName: String): ObjectNode {
        return get(propertyName) as? ObjectNode ?: ObjectNode(nodeFactory).also { set<ObjectNode>(propertyName, it) }
    }

    protected fun ObjectNode.allOf(): ArrayNode {
        return get("allOf") as? ArrayNode ?: ArrayNode(nodeFactory).also { set<ObjectNode>("allOf", it) }
    }

    protected fun String.toPropertiesObjectNodeIn(jsonSchema: ObjectNode): ObjectNode {
        return split('.').fold(jsonSchema) { node: ObjectNode, property: String ->
            node.objectNode("properties").objectNode(property)
        }
    }
}
