package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("NestedLambdaShadowedImplicitParameter")
internal class JsonSchemaAggregatorTest {
    private lateinit var testProject: TestProject
    private lateinit var aggregator: JsonSchemaAggregator

    private val jsonMapper = ObjectMapper()

    companion object {
        private const val REPOSITORY_URL = "http://localhost:1980"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        private const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        val repositoryMappings = mapOf(APPS to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"))
        aggregator =
            JsonSchemaAggregator(repositoryMappings, testProject.downloadSchemasDir, testProject.extractSchemasDir)
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `aggregate should generate JSON schema for assistance in IDE`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(
            json,
            {
                it.node("\$schema").isEqualTo(SCHEMA_VERSION)
                it.node("\$id").isEqualTo("$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
                it.node("title").isEqualTo("Configuration for chart $CHART_NAME:$CHART_VERSION")
                it.node("description").isEqualTo("\\n\\\\n")
            },
        )
    }

    @Test
    fun `aggregate should aggregate downloaded JSON schemas for dependencies in mapped repositories`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(NO_SCHEMA, "0.1.0", THIRDPARTY),
        ))
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                it.node("global.allOf").isArray.hasSize(2)
                it.node("global.allOf[0].\$ref")
                    .isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE#/properties/global")
                it.node("global.allOf[1].\$ref").isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA/$GLOBAL_VALUES_SCHEMA_FILE")
                it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA/$VALUES_SCHEMA_FILE")
                it.isObject.doesNotContainKey(NO_SCHEMA)
            }
        )
    }

    @Test
    fun `aggregate should disable additional properties`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(NO_SCHEMA, "0.1.0", THIRDPARTY),
        ))
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json,
            {
                it.node("additionalProperties").isBoolean.isFalse
                it.node("properties.global.additionalProperties").isBoolean.isFalse
            }
        )
    }

    @Test
    fun `aggregate should disable additional global properties for extracted charts`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, "0.1.0", THIRDPARTY)
        ))
        testProject.initExtractedSchemas(EMBEDDED_SUB_SCHEMA)
        testProject.initExtractedSchemas("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                it.node("$EMBEDDED_SUB_SCHEMA.properties.global.additionalProperties").isBoolean.isFalse
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.properties.global.additionalProperties").isBoolean.isFalse
            }
        )
    }

    @Test
    fun `aggregate should update aggregated values schema with aggregated schema patch`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)
        ))
        val aggregatedJsonPatch = jsonPatch(
            """
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """
        )
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, null, aggregatedJsonPatch)
        assertThatJson(json,
            {
                it.node("title").isEqualTo("overridden value")
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            }
        )
    }

    @Test
    fun `aggregate should update aggregated values schema with values schema patch`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)
        ))
        val valuesJsonPatch = jsonPatch(
            """
            [
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """
        )
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, valuesJsonPatch, null)
        assertThatJson(json,
            {
                it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
                it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
            }
        )
    }

    @Test
    fun `aggregate should use alias to aggregate downloaded JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "$EXTERNAL_SCHEMA-alias")
        ))
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                it.node("global.allOf[0].\$ref")
                    .isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA-alias/$VALUES_SCHEMA_FILE#/properties/global")
                it.node("global.allOf[1].\$ref")
                    .isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA-alias/$GLOBAL_VALUES_SCHEMA_FILE")
                it.node("$EXTERNAL_SCHEMA-alias.\$ref")
                    .isEqualTo("$DOWNLOADS_DIR/$EXTERNAL_SCHEMA-alias/$VALUES_SCHEMA_FILE")
            }
        )
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, "0.1.0", THIRDPARTY)
        ))
        testProject.initExtractedSchemas(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSchemaFile = "$EXTRACT_DIR/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSchemaFile)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile#/properties/global")
            }
        )
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, "0.1.0", THIRDPARTY)
        ))
        testProject.initExtractedSchemas("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSubSchemaFile = "$EXTRACT_DIR/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node(EMBEDDED_SUB_SCHEMA).isObject.doesNotContainKey("\$ref")
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSubSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.properties").isObject.containsOnlyKeys("global")
                it.node("global.allOf").isArray.hasSize(1)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                it.node("$EMBEDDED_SUB_SCHEMA.properties").and({
                    it.node("global.allOf").isArray.hasSize(1)
                    it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                })
            }
        )
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from chart & sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, "0.1.0", THIRDPARTY)
        ))
        testProject.initExtractedSchemas(EMBEDDED_SUB_SCHEMA)
        testProject.initExtractedSchemas("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSchemaFile = "$EXTRACT_DIR/$EMBEDDED_SUB_SCHEMA/$HELM_SCHEMA_FILE"
                val extractedSubSchemaFile = "$EXTRACT_DIR/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node("$EMBEDDED_SUB_SCHEMA.\$ref").isEqualTo(extractedSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties").isObject.doesNotContainKey(HELM_SCHEMA_FILE)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSubSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.properties").isObject.containsOnlyKeys("global")
                it.node("global.allOf").isArray.hasSize(2)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile#/properties/global")
                it.node("global.allOf[1].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                it.node("$EMBEDDED_SUB_SCHEMA.properties").and({
                    it.node("global.allOf").isArray.hasSize(1)
                    it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                }
                )
            }
        )
    }

    @Test
    fun `aggregate should use alias to aggregate extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, "0.1.0", THIRDPARTY, "$EMBEDDED_SCHEMA-alias")
        ))
        testProject.initExtractedSchemas("$EMBEDDED_SCHEMA-alias")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json,
            {
                it.node("properties.$EMBEDDED_SCHEMA-alias.\$ref")
                    .isEqualTo("$EXTRACT_DIR/$EMBEDDED_SCHEMA-alias/$HELM_SCHEMA_FILE")
            }
        )
    }

    @Test
    fun `aggregate should give precedence to downloaded JSON schema over extracted JSON schema`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EMBEDDED_SCHEMA, "0.1.0", APPS)))
        val json = aggregator.aggregate(chart, null, null)
        testProject.initDownloadedSchemas(EMBEDDED_SCHEMA)
        testProject.initExtractedSchemas(EMBEDDED_SCHEMA)
        assertThatJson(json,
            {
                it.node("properties.$EMBEDDED_SCHEMA.\$ref")
                    .isEqualTo("$DOWNLOADS_DIR/$EMBEDDED_SCHEMA/$VALUES_SCHEMA_FILE")
            }
        )
    }

    @Test
    fun `aggregate should set property for dependency condition in values`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
            {
                it.node("title")
                    .isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    @Test
    fun `aggregate should set property for dependency condition for third-party dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, THIRDPARTY, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
            {
                it.node("title")
                    .isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($THIRDPARTY/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    @Test
    fun `aggregate should use alias to document property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "alias", "alias.enabled")
        ))
        testProject.initDownloadedSchemas(EXTERNAL_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.alias.properties.enabled").and(
            {
                it.node("title").isEqualTo("Enable alias dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    private fun jsonPatch(content: String) = JsonPatch.fromJson(jsonMapper.readTree(content))
}
