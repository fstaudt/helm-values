package io.github.fstaudt.helm.aggregation

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.CHART_NAME
import io.github.fstaudt.helm.CHART_VERSION
import io.github.fstaudt.helm.GENERATOR_LABEL
import io.github.fstaudt.helm.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.HELM_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_DESCRIPTION
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.Keywords.Companion.ADDITIONAL_PROPERTIES
import io.github.fstaudt.helm.Keywords.Companion.UNEVALUATED_PROPERTIES
import io.github.fstaudt.helm.SCHEMA_VERSION
import io.github.fstaudt.helm.TestProject
import io.github.fstaudt.helm.TestSchemaLocator
import io.github.fstaudt.helm.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.aggregation.JsonSchemaAggregator.Companion.BASE_URI
import io.github.fstaudt.helm.aggregation.schema.DownloadedSchemaAggregator.Companion.DOWNLOADS
import io.github.fstaudt.helm.aggregation.schema.ExtractedSchemaAggregator.Companion.EXTRACTED_GLOBAL_VALUES_TITLE
import io.github.fstaudt.helm.aggregation.schema.ExtractedSchemaAggregator.Companion.EXTRACTS
import io.github.fstaudt.helm.aggregation.schema.LocalSchemaAggregator.Companion.LOCAL
import io.github.fstaudt.helm.downloadSchemasDir
import io.github.fstaudt.helm.extractsDir
import io.github.fstaudt.helm.initDownloadedSchemas
import io.github.fstaudt.helm.initExtractedHelmDependency
import io.github.fstaudt.helm.initLocalSchema
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import io.github.fstaudt.helm.test.assertions.escaped
import io.github.fstaudt.helm.testProject
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
        private const val DEFS = "\$defs"
        private const val REPOSITORY_URL = "http://localhost:1980"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EXTERNAL_SCHEMAS_PATH = "$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
        private const val EXTERNAL_VALUES_SCHEMA_PATH = "$EXTERNAL_SCHEMAS_PATH/$VALUES_SCHEMA_FILE"
        private const val EXTERNAL_GLOBAL_SCHEMA_PATH = "$EXTERNAL_SCHEMAS_PATH/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val EXTERNAL_SUB_SCHEMA = "external-sub-json-schema"
        private const val EXTERNAL_SUB_SCHEMAS_PATH = "$EXTERNAL_SUB_SCHEMA/$EXTERNAL_VERSION"
        private const val EXTERNAL_SUB_VALUES_SCHEMA_PATH = "$EXTERNAL_SUB_SCHEMAS_PATH/$VALUES_SCHEMA_FILE"
        private const val EXTERNAL_SUB_GLOBAL_SCHEMA_PATH = "$EXTERNAL_SUB_SCHEMAS_PATH/$GLOBAL_VALUES_SCHEMA_FILE"
        private const val VALUES_SCHEMA = "values.json"
        private const val GLOBAL_SCHEMA = "global.json"
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
            TestSchemaLocator(testProject),
            testProject,
            testProject.downloadSchemasDir,
            testProject.extractsDir)
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
            it.node("\$id").isEqualTo("$BASE_URI/$CHART_NAME/$CHART_VERSION/$AGGREGATED_SCHEMA_FILE")
            it.node("x-generated-by").isEqualTo(GENERATOR_LABEL)
            it.node("x-generated-at").isString.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
            it.node("title").isEqualTo("Configuration for chart $CHART_NAME:$CHART_VERSION")
            it.node("description").isEqualTo("\\n\\\\n")
            it.isObject.doesNotContainKey("\$ref")
        })
    }

    @Test
    fun `aggregate should aggregate JSON schema of current chart when it is available`() {
        testProject.initLocalSchema(".")
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).and({
            it.node("allOf[0].\$ref").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
        })
        assertThatJson(json).node("$DEFS.$LOCAL.${HELM_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("./$HELM_SCHEMA_FILE")
            })
    }

    @Test
    fun `aggregate should update internal references in JSON schema of current chart`() {
        testProject.initLocalSchema(".",
            schemaContent = """
                {
                  "${'$'}id": "./$HELM_SCHEMA_FILE",
                  "allOf": [
                    {
                      "${'$'}ref": "#/ref"
                    }
                  ],
                  "ref": {}
                }
            """.trimIndent())
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("$DEFS.$LOCAL.${HELM_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("./$HELM_SCHEMA_FILE")
                it.node("allOf[0].\$ref").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE/ref")
            })
    }

    @Test
    fun `aggregate should should allow additional global properties in JSON schema of current chart`() {
        testProject.initLocalSchema(".",
            schemaContent = """
                {
                  "${'$'}id": "./$HELM_SCHEMA_FILE",
                  "$ADDITIONAL_PROPERTIES": false,
                  "$UNEVALUATED_PROPERTIES": false,
                  "properties": {
                    "global": {
                      "$ADDITIONAL_PROPERTIES": false,
                      "$UNEVALUATED_PROPERTIES": false
                    }
                  }
                }
            """.trimIndent())
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).and({
            it.node("allOf[0].\$ref").isEqualTo("#/$DEFS/$LOCAL/$HELM_SCHEMA_FILE")
        })
        assertThatJson(json).node("$DEFS.$LOCAL.${HELM_SCHEMA_FILE.escaped()}")
            .and({
                it.isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
    }

    @Test
    fun `aggregate should aggregate downloaded JSON schemas for dependencies in mapped repositories`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(NO_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH")
        testProject.initExtractedHelmDependency(NO_SCHEMA, schema = null)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[0].\$ref")
                .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH/properties/global")
            it.node("global.allOf[1].\$ref").isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_GLOBAL_SCHEMA_PATH")
            it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            it.node("$EXTERNAL_SCHEMA.\$ref")
                .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
            it.node(NO_SCHEMA).isObject.isEmpty()
        })
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}")
            .and({
                it.node("${VALUES_SCHEMA_FILE.escaped()}.\$id").isEqualTo("$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
            })
    }

    @Test
    fun `aggregate should aggregate downloaded JSON schemas for dependencies in repository with custom schema names`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH",
            valuesSchemaFile = VALUES_SCHEMA,
            globalSchemaFile = GLOBAL_SCHEMA)
        val customRepositoryMappings = mapOf(
            APPS to JsonSchemaRepository("$REPOSITORY_URL/$APPS_PATH", null, null, VALUES_SCHEMA, GLOBAL_SCHEMA)
        )
        val aggregator = JsonSchemaAggregator(
            customRepositoryMappings,
            TestSchemaLocator(testProject),
            testProject,
            testProject.downloadSchemasDir,
            testProject.extractsDir)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf").isArray.hasSize(3)
            it.node("global.allOf[0].\$ref")
                .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMAS_PATH/$VALUES_SCHEMA/properties/global")
            it.node("global.allOf[1].\$ref")
                .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMAS_PATH/$GLOBAL_SCHEMA")
            it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            it.node("$EXTERNAL_SCHEMA.\$ref")
                .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMAS_PATH/$VALUES_SCHEMA")
            it.isObject.doesNotContainKey(NO_SCHEMA)
        })
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}")
            .and({
                it.node("${VALUES_SCHEMA.escaped()}.\$id").isEqualTo("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH/$VALUES_SCHEMA")
            })
    }

    @Test
    fun `aggregate should aggregate sub-schemas referenced in downloaded JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH",
            valuesSchemaContent = """
                {
                    "${'$'}id": "$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH",
                    "${'$'}ref": "../../$EXTERNAL_SUB_VALUES_SCHEMA_PATH"
                }
            """.trimIndent())
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SUB_SCHEMAS_PATH")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SUB_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should allow additional and unevaluated global properties in downloaded schemas & sub-schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH",
            valuesSchemaContent = """
                {
                  "${'$'}id": "$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH",
                  "properties": {
                    "global": {
                      "allOf": [
                        {
                          "${'$'}ref": "../../$EXTERNAL_SUB_VALUES_SCHEMA_PATH#/properties/global"
                        },
                        {
                          "${'$'}ref": "../../$EXTERNAL_SUB_GLOBAL_SCHEMA_PATH"
                        }
                      ],
                      "$ADDITIONAL_PROPERTIES": false,
                      "$UNEVALUATED_PROPERTIES": false
                    }
                  },
                  "$ADDITIONAL_PROPERTIES": false,
                  "$UNEVALUATED_PROPERTIES": false
              }
            """.trimIndent())
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SUB_SCHEMAS_PATH",
            valuesSchemaContent = """
                {
                  "${'$'}id": "$APPS_PATH/$EXTERNAL_SUB_SCHEMAS_PATH/$VALUES_SCHEMA_FILE",
                  "properties": {
                    "global": {
                      "$ADDITIONAL_PROPERTIES": false,
                      "$UNEVALUATED_PROPERTIES": false
                    }
                  }
              }
            """.trimIndent(),
            globalSchemaContent = """
                {
                  "${'$'}id": "$APPS_PATH/$EXTERNAL_SUB_SCHEMAS_PATH/$GLOBAL_VALUES_SCHEMA_FILE",
                  "$ADDITIONAL_PROPERTIES": false,
                  "$UNEVALUATED_PROPERTIES": false
                }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
                it.isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH")
                it.node("properties.global").isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${GLOBAL_VALUES_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("$APPS_PATH/$EXTERNAL_SUB_GLOBAL_SCHEMA_PATH")
                it.isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
    }

    @Test
    fun `aggregate should aggregate sub-schemas referenced in downloaded JSON schemas with anchor`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH",
            valuesSchemaContent = """
                {
                    "${'$'}id": "$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH",
                    "${'$'}ref": "../../$EXTERNAL_SUB_VALUES_SCHEMA_PATH#/anchor"
                }
            """.trimIndent())
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SUB_SCHEMAS_PATH",
            valuesSchemaContent = """
                {
                    "${'$'}id": "$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH",
                    "anchor": {
                        "${'$'}ref": "$GLOBAL_VALUES_SCHEMA_FILE#/anchor"
                    }
                }
            """.trimIndent(),
            globalSchemaContent = """
                {
                    "${'$'}id": "$APPS_PATH/$EXTERNAL_SUB_GLOBAL_SCHEMA_PATH",
                    "anchor": {}
                }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH/anchor")
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}.anchor.\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SUB_GLOBAL_SCHEMA_PATH/anchor")
        assertThatJson(json).node("$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${GLOBAL_VALUES_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$APPS_PATH/$EXTERNAL_SUB_GLOBAL_SCHEMA_PATH")
    }

    @Test
    fun `aggregate should update internal references in downloaded JSON schemas & sub-schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH",
            valuesSchemaContent = """
              {
                "${'$'}id": "$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH",
                "refs": {
                  "downloaded": {
                    "${'$'}ref": "../../$EXTERNAL_SUB_VALUES_SCHEMA_PATH"
                  },
                  "internal": {
                    "${'$'}ref": "#/internal"
                  }
                },
                "internal": {}
              }
            """.trimIndent())
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SUB_SCHEMAS_PATH",
            valuesSchemaContent = """
              {
                "${'$'}id": "$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH",
                "refs": {
                  "${'$'}ref": "#/internal"
                },
                "internal": {}
              }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        val schemaNode =
            "$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}"
        val subSchemaNode =
            "$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SUB_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}"
        assertThatJson(json).node("$schemaNode.\$id").isEqualTo("$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$subSchemaNode.\$id").isEqualTo("$APPS_PATH/$EXTERNAL_SUB_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$schemaNode.refs.downloaded.\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SUB_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
        assertThatJson(json).node("$schemaNode.refs.internal.\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE/internal")
        assertThatJson(json).node("$subSchemaNode.refs.\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SUB_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE/internal")
    }

    @Test
    fun `aggregate should remove invalid internal references from aggregated JSON schema`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH",
            valuesSchemaContent = """
              {
                "${'$'}id": "$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH",
                "properties": {
                  "global": {
                    "refs": {
                      "invalid": {
                        "${'$'}ref": "../\"invalid"
                      }
                    }
                  }
                }
              }
            """.trimIndent(),
            globalSchemaFile = "invalid.schema.json")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.global.allOf[0].\$ref")
            .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE/properties/global")
        assertThatJson(json).node("properties.global.allOf[1]").and({
            it.isObject.doesNotContainKey("\$ref")
            it.node("_comment")
                .isEqualTo("removed invalid \$ref #/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE")
        })
        val schemaNode =
            "$DEFS.$DOWNLOADS.$APPS_PATH.$EXTERNAL_SCHEMA.${EXTERNAL_VERSION.escaped()}.${VALUES_SCHEMA_FILE.escaped()}"
        assertThatJson(json).node("$schemaNode.\$id").isEqualTo("$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
        assertThatJson(json).node("$schemaNode.properties.global.refs.invalid").and({
            it.isObject.doesNotContainKey("\$ref")
            it.node("_comment").isEqualTo("removed invalid \$ref ../\\\"invalid")
        })
    }

    @Test
    fun `aggregate should skip dependencies without version`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, null, APPS, "no-version")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").isObject.doesNotContainKeys(EMBEDDED_SCHEMA, "no-version")
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
    fun `aggregate should disable additional and unevaluated properties`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(NO_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).and({
            it.node(ADDITIONAL_PROPERTIES).isBoolean.isFalse
            it.node(UNEVALUATED_PROPERTIES).isBoolean.isFalse
        })
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
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "alias")
        ))
        testProject.initDownloadedSchemas("$APPS_PATH/$EXTERNAL_SCHEMAS_PATH")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            it.node("global.allOf[0].\$ref")
                .isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH/properties/global")
            it.node("global.allOf[1].\$ref").isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_GLOBAL_SCHEMA_PATH")
            it.node("alias.\$ref").isEqualTo("#/$DEFS/$DOWNLOADS/$APPS_PATH/$EXTERNAL_VALUES_SCHEMA_PATH")
        })
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            val extractedSchemaFile = "#/$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
            it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSchemaFile)
            it.node(EMBEDDED_SCHEMA).isObject.containsOnlyKeys("\$ref")
            it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile/properties/global")
        })
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SUB_SCHEMA,
            chartDependencies = """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: $CHART_VERSION
            """.trimIndent(),
            schema = null)
        testProject.initExtractedHelmDependency("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSubSchemaFile =
                    "#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node("global.allOf").isArray.hasSize(2)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile/properties/global")
                it.node(EMBEDDED_SUB_SCHEMA).isObject.doesNotContainKey("\$ref")
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSubSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA").isObject.containsOnlyKeys("\$ref")
                it.node("$EMBEDDED_SUB_SCHEMA.properties").and({
                    it.node("global.allOf").isArray.hasSize(2)
                    it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile/properties/global")
                    it.node("global.allOf[1].title").isString.startsWith(EXTRACTED_GLOBAL_VALUES_TITLE)
                })
                it.node("global.allOf[1].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            }
        )
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SUB_SCHEMA.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from sub-chart when sub-chart dependency was stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SUB_SCHEMA,
            chartDependencies = """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: $CHART_VERSION
                  repository: "file://../$EMBEDDED_SCHEMA"
            """.trimIndent(),
            schema = null)
        testProject.initExtractedHelmDependency("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSubSchemaFile =
                    "#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node("global.allOf").isArray.hasSize(2)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile/properties/global")
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSubSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA").isObject.containsOnlyKeys("\$ref")
            }
        )
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SUB_SCHEMA.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas from chart & sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SUB_SCHEMA,
            chartDependencies = """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: $CHART_VERSION
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and(
            {
                val extractedSchemaFile = "#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/$HELM_SCHEMA_FILE"
                val extractedSubSchemaFile =
                    "#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
                it.node("global.allOf").isArray.hasSize(3)
                it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile/properties/global")
                it.node("global.allOf[1].\$ref").isEqualTo("$extractedSubSchemaFile/properties/global")
                it.node("$EMBEDDED_SUB_SCHEMA.allOf[0].\$ref").isEqualTo(extractedSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties").isObject.doesNotContainKey(HELM_SCHEMA_FILE)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSubSchemaFile)
                it.node("$EMBEDDED_SUB_SCHEMA.properties.$EMBEDDED_SCHEMA").isObject.containsOnlyKeys("\$ref")
                it.node("$EMBEDDED_SUB_SCHEMA.properties").and({
                    it.node("global.allOf").isArray.hasSize(2)
                    it.node("global.allOf[0].\$ref").isEqualTo("$extractedSubSchemaFile/properties/global")
                    it.node("global.allOf[1].title")
                        .isEqualTo("$EXTRACTED_GLOBAL_VALUES_TITLE $EMBEDDED_SUB_SCHEMA dependency")
                    it.node("global.allOf[1].description").isEqualTo("\\n\\\\n")
                })
                it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            }
        )
        assertThatJson(json).node("$DEFS.$EXTRACTS").and({
            it.node("$EMBEDDED_SUB_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
                .isEqualTo("$EMBEDDED_SUB_SCHEMA/$HELM_SCHEMA_FILE")
            it.node("$EMBEDDED_SUB_SCHEMA.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
                .isEqualTo("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
        })
    }

    @Test
    fun `aggregate should allow additional and unevaluated global properties in extracted JSON schemas from chart & sub-chart`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SUB_SCHEMA,
            chartDependencies = """
                dependencies:
                - name: $EMBEDDED_SCHEMA
                  version: $CHART_VERSION
            """.trimIndent(),
            schema = """
                {
                  "${'$'}id": "$EMBEDDED_SUB_SCHEMA/$HELM_SCHEMA_FILE",
                  "$ADDITIONAL_PROPERTIES": false,
                  "$UNEVALUATED_PROPERTIES": false,
                  "properties": {
                    "global": {
                      "$ADDITIONAL_PROPERTIES": false,
                      "$UNEVALUATED_PROPERTIES": false
                    }
                  }
                }
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA",
            schema = """
                {
                  "${'$'}id": "$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE",
                  "$ADDITIONAL_PROPERTIES": false,
                  "$UNEVALUATED_PROPERTIES": false,
                  "properties": {
                    "global": {
                      "$ADDITIONAL_PROPERTIES": false,
                      "$UNEVALUATED_PROPERTIES": false
                    }
                  }
                }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("$DEFS.$EXTRACTS").and({
            it.node("$EMBEDDED_SUB_SCHEMA.${HELM_SCHEMA_FILE.escaped()}").and({
                it.node("\$id").isEqualTo("$EMBEDDED_SUB_SCHEMA/$HELM_SCHEMA_FILE")
                it.isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
            it.node("$EMBEDDED_SUB_SCHEMA.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}").and({
                it.node("\$id").isEqualTo("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
                it.isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
        })
    }

    @Test
    fun `aggregate should aggregate fallback schema when dependency archive is missing or invalid`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SCHEMA,
            chartDependencies = null,
            schema = """
                {
                  "${'$'}id": "fallback"
                }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EMBEDDED_SCHEMA.\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("fallback")
    }

    @Test
    fun `aggregate should use alias to aggregate extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY, "alias")
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.alias.\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/alias/$HELM_SCHEMA_FILE")
        assertThatJson(json).node("$DEFS.$EXTRACTS.alias.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
        assertThatJson(json).node("properties.global.allOf[0].\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/alias/$HELM_SCHEMA_FILE/properties/global")
    }

    @Test
    fun `aggregate should use alias to aggregate extracted JSON schemas from sub-charts`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SUB_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SUB_SCHEMA,
            chartDependencies = """
            dependencies:
            - name: $EMBEDDED_SCHEMA
              version: $CHART_VERSION
              alias: alias
            """.trimIndent())
        testProject.initExtractedHelmDependency("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA")
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EMBEDDED_SUB_SCHEMA.properties.alias.\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/alias/$HELM_SCHEMA_FILE")
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SUB_SCHEMA.alias.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$EMBEDDED_SUB_SCHEMA/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
        assertThatJson(json).node("properties.global.allOf[0].\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/$HELM_SCHEMA_FILE/properties/global")
        assertThatJson(json).node("properties.global.allOf[1].\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/alias/$HELM_SCHEMA_FILE/properties/global")
        assertThatJson(json).node("properties.$EMBEDDED_SUB_SCHEMA.properties.global.allOf[0].\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SUB_SCHEMA/alias/$HELM_SCHEMA_FILE/properties/global")
    }

    @Test
    fun `aggregate should aggregate extracted JSON schemas for dependency without repository`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, null)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SCHEMA)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EMBEDDED_SCHEMA.\$ref")
            .isEqualTo("#/$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.\$id")
            .isEqualTo("$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE")
    }

    @Test
    fun `aggregate should update internal references in extracted JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY)
        ))
        testProject.initExtractedHelmDependency(EMBEDDED_SCHEMA,
            schema = """
            {
              "${'$'}id": "$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE",
              "properties": {
                "global": {
                  "refs": {
                    "${'$'}ref": "#/internal"
                  }
                }
              },
              "internal": {}
            }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        val extractedSchemaFile = "#/$DEFS/$EXTRACTS/$EMBEDDED_SCHEMA/$HELM_SCHEMA_FILE"
        assertThatJson(json).node("properties").and({
            it.node("$EMBEDDED_SCHEMA.\$ref").isEqualTo(extractedSchemaFile)
            it.node("global.allOf[0].\$ref").isEqualTo("$extractedSchemaFile/properties/global")
        })
        assertThatJson(json).node("$DEFS.$EXTRACTS.$EMBEDDED_SCHEMA.${HELM_SCHEMA_FILE.escaped()}.properties.global.refs.\$ref")
            .isEqualTo("$extractedSchemaFile/internal")
    }

    @Test
    fun `aggregate should set property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "deps.$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.deps.properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `aggregate should set property for dependency condition in allOf when it conflicts with $ref`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.allOf[0]").isObject.containsOnlyKeys("\$ref")
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `aggregate should set properties for dependency condition when condition contains comma`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS,
                condition = "deps.$EXTERNAL_SCHEMA.enabled,other.$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.deps.properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
        assertThatJson(json).node("properties.other.properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `aggregate should set property for dependency condition when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA",
                condition = "deps.$EXTERNAL_SCHEMA.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.deps.properties.$EXTERNAL_SCHEMA.properties.enabled").and({
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
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "alias", "deps.alias.enabled")
        ))
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties.deps.properties.alias.properties.enabled").and(
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
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://sub/$EXTERNAL_SCHEMA")
        ))
        testProject.initExtractedHelmDependency(EXTERNAL_SCHEMA)
        testProject.initLocalSchema("sub/$EXTERNAL_SCHEMA", AGGREGATED_SCHEMA_FILE)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            val subChartAggregatedSchemaFile = "#/$DEFS/$LOCAL/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(subChartAggregatedSchemaFile)
            it.node("global.allOf[0].\$ref").isEqualTo("$subChartAggregatedSchemaFile/properties/global")
            it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
        })
        assertThatJson(json).node("$DEFS.$LOCAL.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("sub/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
            })
    }

    @Test
    fun `aggregate should aggregate aggregated JSON schema of dependency when locally stored dependency is provided with version range`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, "^$EXTERNAL_VERSION", "file://sub/$EXTERNAL_SCHEMA")
        ))
        testProject.initLocalSchema("sub/$EXTERNAL_SCHEMA", AGGREGATED_SCHEMA_FILE)
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("properties").and({
            val subChartAggregatedSchemaFile = "#/$DEFS/$LOCAL/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(subChartAggregatedSchemaFile)
            it.node("global.allOf[0].\$ref").isEqualTo("$subChartAggregatedSchemaFile/properties/global")
            it.node("global.allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
        })
        assertThatJson(json).node("$DEFS.$LOCAL.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("sub/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
            })
    }

    @Test
    fun `aggregate should update internal references in aggregated JSON schema of locally stored dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://sub/$EXTERNAL_SCHEMA")
        ))
        testProject.initLocalSchema("sub/$EXTERNAL_SCHEMA", AGGREGATED_SCHEMA_FILE,
            schemaContent = """
                {
                  "${'$'}id": "sub/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE",
                  "allOf": [
                    {
                      "${'$'}ref": "#/ref"
                    }
                  ],
                  "ref": {}
                }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        val subChartAggregatedSchemaFile = "#/$DEFS/$LOCAL/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
        assertThatJson(json).node("properties").and({
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(subChartAggregatedSchemaFile)
        })
        assertThatJson(json).node("$DEFS.$LOCAL.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}")
            .and({
                it.node("\$id").isEqualTo("sub/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE")
                it.node("allOf[0].\$ref").isEqualTo("$subChartAggregatedSchemaFile/ref")
            })
    }

    @Test
    fun `aggregate should use an empty schema if aggregated JSON schema of locally stored dependency doesn't exist`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://sub/$EXTERNAL_SCHEMA")
        ))
        val json = aggregator.aggregate(chart, null, null)
        val subChartAggregatedSchemaFile = "#/$DEFS/$LOCAL/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE"
        assertThatJson(json).node("properties").and({
            it.node("$EXTERNAL_SCHEMA.\$ref").isEqualTo(subChartAggregatedSchemaFile)
        })
        assertThatJson(json).node("$DEFS.$LOCAL.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}").isObject
    }

    @Test
    fun `aggregate should allow additional and unevaluated global properties in aggregated JSON schema of locally stored dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://sub/$EXTERNAL_SCHEMA")
        ))
        testProject.initLocalSchema("sub/$EXTERNAL_SCHEMA", AGGREGATED_SCHEMA_FILE,
            schemaContent = """
                {
                  "${'$'}id": "sub/$EXTERNAL_SCHEMA/$AGGREGATED_SCHEMA_FILE",
                  "$ADDITIONAL_PROPERTIES": false,
                  "$UNEVALUATED_PROPERTIES": false,
                  "properties": {
                    "global": {
                      "$ADDITIONAL_PROPERTIES": false,
                      "$UNEVALUATED_PROPERTIES": false
                    }
                  }
                }
            """.trimIndent())
        val json = aggregator.aggregate(chart, null, null)
        assertThatJson(json).node("$DEFS.$LOCAL.$EXTERNAL_SCHEMA.${AGGREGATED_SCHEMA_FILE.escaped()}")
            .and({
                it.isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(ADDITIONAL_PROPERTIES)
                it.node("properties.global").isObject.doesNotContainKey(UNEVALUATED_PROPERTIES)
            })
    }

    private fun jsonPatch(content: String) = JsonPatch.fromJson(jsonMapper.readTree(content))
}