package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaAggregator.Companion.EXTRACTED_GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.JsonSchemaDownloader.Companion.DOWNLOADS_DIR
import io.github.fstaudt.helm.JsonSchemaExtractor.Companion.EXTRACT_DIR
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_DESCRIPTION
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

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
        private const val EXTERNAL_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE"
        private const val EXTERNAL_GLOBAL_SCHEMA_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_VERSION = "0.1.0"
        private const val EMBEDDED_SUB_SCHEMA = "embedded-sub-json-schema"
        private const val NO_SCHEMA = "no-json-schema"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        val repositoryMappings = mapOf(APPS to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH"))
        aggregator = JsonSchemaAggregator(
            repositoryMappings,
            TestSchemaLocator(),
            testProject,
            testProject.downloadSchemasDir,
            testProject.extractSchemasDir)
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `aggregate should generate JSON schema for assistance in IDE`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).and({
            it.node("\$schema").isEqualTo(SCHEMA_VERSION)
            it.node("\$id").isEqualTo("$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            it.node("x-generated-by").isEqualTo(GENERATOR_LABEL)
            it.node("x-generated-at").isString.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
            it.node("title").isEqualTo("Configuration for chart $CHART_NAME:$CHART_VERSION")
            it.node("description").isEqualTo("\\n\\\\n")
            it.isObject.doesNotContainKey("\$ref")
        })
    }

    @Test
    fun `aggregate should aggregate JSON schema of current chart when it is available`() {
        File(testProject, HELM_SCHEMA_FILE).writeText("{}")
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).and({
            it.node("\$ref").isEqualTo("${testProject.name}/$HELM_SCHEMA_FILE")
        })
    }

    @Test
    fun `aggregate should aggregate downloaded JSON schemas for dependencies in mapped repositories`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(NO_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[0].\$ref")
                .isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_SCHEMA_PATH#/properties/global")
            it.node("global.allOf[1].\$ref").isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_GLOBAL_SCHEMA_PATH")
            it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_SCHEMA_PATH")
            it.isObject.doesNotContainKey(NO_SCHEMA)
        })
    }

    @Test
    fun `aggregate should skip dependencies without version`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, null, APPS, "no-version")
        ))
        testProject.initExtractedSchemas(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").isObject.doesNotContainKeys(EXTERNAL_SCHEMA, "no-version")
    }

    @Test
    fun `aggregate should document list of dependencies in description of global values`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[2].title").isEqualTo("$GLOBAL_VALUES_TITLE $CHART_NAME:$CHART_VERSION")
            it.node("global.allOf[2].description").isString
                .contains(GLOBAL_VALUES_DESCRIPTION)
                .contains("$APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
                .contains("$THIRDPARTY/$EMBEDDED_SCHEMA:$EMBEDDED_VERSION")
            it.node("global.allOf[2].x-intellij-html-description").isString
                .contains(GLOBAL_VALUES_DESCRIPTION)
                .contains("$APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
                .contains("$REPOSITORY_URL/$APPS_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION")
                .contains("$THIRDPARTY/$EMBEDDED_SCHEMA:$EMBEDDED_VERSION")
        })
    }

    @Test
    fun `aggregate should document list of dependencies in description of global values when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[2].title").isEqualTo("$GLOBAL_VALUES_TITLE $CHART_NAME:$CHART_VERSION")
            it.node("global.allOf[2].description").isString
                .contains(GLOBAL_VALUES_DESCRIPTION)
                .contains("- $EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
            it.node("global.allOf[2].x-intellij-html-description").isString
                .contains(GLOBAL_VALUES_DESCRIPTION)
                .contains("<li>$EXTERNAL_SCHEMA:$EXTERNAL_VERSION</li>")
        })
    }

    @Test
    fun `aggregate should disable additional properties`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(NO_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).and({
            it.node("additionalProperties").isBoolean.isFalse
            it.node("properties.global.additionalProperties").isBoolean.isFalse
        })
    }

    @Test
    fun `aggregate should disable additional global properties for extracted charts`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
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
        val json = aggregator.aggregate(chart, null, aggregatedJsonPatch)
        assertThatJson(json).and({
            it.node("title").isEqualTo("overridden value")
            it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
            it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
        })
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
        val json = aggregator.aggregate(chart, valuesJsonPatch, null)
        assertThatJson(json).and({
            it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
            it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
        })
    }

    @Test
    fun `aggregate should use alias to aggregate downloaded JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "$EXTERNAL_SCHEMA-alias")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf[0].\$ref")
                .isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_SCHEMA_PATH#/properties/global")
            it.node("global.allOf[1].\$ref").isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_GLOBAL_SCHEMA_PATH")
            it.node("$EXTERNAL_SCHEMA-alias.\$ref").isEqualTo("$DOWNLOADS_DIR/$APPS_PATH/$EXTERNAL_SCHEMA_PATH")
        })
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedSchemas(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            val extractedSchemaFile = "$EXTRACT_DIR/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
            it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSchemaFile)
            it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile#/properties/global")
        })
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedSchemas("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSubSchemaFile = "$EXTRACT_DIR/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node(EMBEDDED_SUB_SCHEMA).isObject.doesNotContainKey("\$ref")
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSubSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.properties").isObject.containsOnlyKeys("global")
                it.node("global.allOf").isArray.hasSize(2)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                it.node("$EMBEDDED_SUB_SCHEMA.properties").and({
                    it.node("global.allOf").isArray.hasSize(2)
                    it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                    it.node("global.allOf[1].title").isString.startsWith(EXTRACTED_GLOBAL_VALUES_TITLE)
                })
                it.node("global.allOf[1].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            }
        )
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from chart & sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
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
                it.node("global.allOf").isArray.hasSize(3)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile#/properties/global")
                it.node("global.allOf[1].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                it.node("$EMBEDDED_SUB_SCHEMA.properties").and({
                    it.node("global.allOf").isArray.hasSize(2)
                    it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile#/properties/global")
                    it.node("global.allOf[1].title")
                        .isEqualTo("$EXTRACTED_GLOBAL_VALUES_TITLE $EMBEDDED_SUB_SCHEMA dependency")
                    it.node("global.allOf[1].description").isEqualTo("\\n\\\\n")
                    it.node("$EMBEDDED_SCHEMA.properties").and({
                        it.node("global.allOf").isArray.hasSize(1)
                        it.node("global.allOf[0].title")
                            .isEqualTo("$EXTRACTED_GLOBAL_VALUES_TITLE $EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA dependency")
                        it.node("global.allOf[0].description").isEqualTo("\\n\\\\n")
                    })
                })
                it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            }
        )
    }

    @Test
    fun `aggregate should use alias to aggregate extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY, "$EMBEDDED_SCHEMA-alias")
        ))
        testProject.initExtractedSchemas("$EMBEDDED_SCHEMA-alias")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EMBEDDED_SCHEMA-alias.\$ref")
            .isEqualTo("$EXTRACT_DIR/$EMBEDDED_SCHEMA-alias/$HELM_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas for dependency without repository`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, null)
        ))
        testProject.initExtractedSchemas(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EMBEDDED_SCHEMA.\$ref")
            .isEqualTo("$EXTRACT_DIR/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should set property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `aggregate should set property for dependency condition when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA",
                condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
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
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.alias.properties.enabled").and(
            {
                it.node("title").isEqualTo("Enable alias dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    @Test
    fun `aggregate should aggregate aggregated JSON schema of dependency when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://sub-charts/$EXTERNAL_SCHEMA")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            val subChartAggregatedSchemaFile = "sub-charts/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(subChartAggregatedSchemaFile)
            it.node("global.allOf[0].\$ref").isEqualTo("$subChartAggregatedSchemaFile#/properties/global")
            it.node("global.allOf[2]")
        })
    }

    private fun jsonPatch(content: String) = JsonPatch.fromJson(jsonMapper.readTree(content))
}
