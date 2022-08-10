package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.model.Chart
import io.github.fstaudt.helm.model.ChartDependency
import io.github.fstaudt.helm.model.JsonSchemaRepository
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class JsonSchemaGeneratorTest {
    private lateinit var testProject: TestProject
    private lateinit var generator: JsonSchemaGenerator

    private val jsonMapper = ObjectMapper()

    companion object {
        private const val BASE_URL = "http://charts"
        private const val APPS = "@apps"
        private const val APPS_PATH = "apps"
        private const val BUNDLES = "@bundles"
        private const val BUNDLES_PATH = "bundles"
        private const val INFRA = "@infra"
        private const val INFRA_REPOSITORY_URL = "http://infra.charts"
        private const val THIRDPARTY = "@thirdparty"
        private const val EXTERNAL_SCHEMA = "external-json-schema"
        private const val EXTERNAL_VERSION = "0.2.0"
        private const val EMBEDDED_SCHEMA = "embedded-json-schema"
        private const val EMBEDDED_VERSION = "0.3.0"
        private const val BASE_CHART_URL = "$BASE_URL/$APPS_PATH/$CHART_NAME/$CHART_VERSION"
    }

    @BeforeEach
    fun `init test project`() {
        testProject = testProject()
        val repositoryMappings = mapOf(
            APPS to JsonSchemaRepository("$BASE_URL/$APPS_PATH"),
            BUNDLES to JsonSchemaRepository("$BASE_URL/$BUNDLES_PATH"),
            INFRA to JsonSchemaRepository(INFRA_REPOSITORY_URL)
        )
        generator = JsonSchemaGenerator(repositoryMappings, repositoryMappings[APPS]!!)
    }

    @AfterEach
    fun `cleanup test project`() {
        testProject.deleteRecursively()
    }

    @Test
    fun `generateValuesJsonSchema should generate values JSON schema with dependencies in mapped repositories`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
            { it.node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE") },
            { it.node("title").isEqualTo("Configuration for chart $BASE_CHART_URL") },
            { it.node("description").isEqualTo("\\n\\\\n ") },
            { it.node("properties").isObject.containsKey(EXTERNAL_SCHEMA) },
            { it.node("properties").isObject.doesNotContainKey(EMBEDDED_SCHEMA) },
            { it.node("properties.global.\$ref").isEqualTo(GLOBAL_VALUES_SCHEMA_FILE) },
        )
    }

    @Test
    fun `generateValuesJsonSchema should use alias as property names`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "$EXTERNAL_SCHEMA-alias")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("properties").isObject.doesNotContainKey(EXTERNAL_SCHEMA) },
            {
                it.node("properties.$EXTERNAL_SCHEMA-alias.\$ref")
                    .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should disable additional properties`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("additionalProperties").isBoolean.isFalse },
            { it.node("properties.global.additionalProperties").isBoolean.isFalse }
        )
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas when repositories are same`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            {
                it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                    .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas when repositories have same host`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, BUNDLES)))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            {
                it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                    .isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should use full ref to external JSON schemas when repositories are different`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, INFRA)))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            {
                it.node("properties.$EXTERNAL_SCHEMA.\$ref")
                    .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should set property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
            {
                it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n ")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should set property for dependency condition for third-party dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, THIRDPARTY, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and(
            {
                it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($THIRDPARTY/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n ")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should use alias to document property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "$EXTERNAL_SCHEMA-alias", "$EXTERNAL_SCHEMA-alias.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA-alias.properties.enabled").and(
            {
                it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA-alias dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
                it.node("description").isEqualTo("\\n\\\\n ")
                it.node("type").isEqualTo("boolean")
            },
        )
    }

    @Test
    fun `generateValuesJsonSchema should update generated schema with patch when it is provided`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)))
        val jsonPatch = jsonPatch("""
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """)
        val json = generator.generateValuesJsonSchema(chart, jsonPatch)
        assertThatJson(json).and(
            { it.node("title").isEqualTo("overridden value") },
            { it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value") },
            { it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref") },
        )
    }

    @Test
    fun `generateGlobalValuesSchema should generate global values JSON schema with dependencies in mapped repositories`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = generator.generateGlobalValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
            { it.node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE") },
            { it.node("title").isEqualTo("Configuration of global values for chart $BASE_CHART_URL") },
            { it.node("description").isEqualTo("\\n\\\\n ") },
            { it.node("allOf").isArray.hasSize(1) },
            { it.node("allOf[0].\$ref").isString.contains(EXTERNAL_SCHEMA) },
        )
    }

    @Test
    fun `generateGlobalValuesSchema should generate empty JSON schema when there are no dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = generator.generateGlobalValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("\$schema").isEqualTo(SCHEMA_VERSION) },
            { it.node("\$id").isEqualTo("$BASE_CHART_URL/$GLOBAL_VALUES_SCHEMA_FILE") },
            { it.node("title").isEqualTo("Configuration of global values for chart $BASE_CHART_URL") },
            { it.node("description").isEqualTo("\\n\\\\n ") },
            { it.isObject.doesNotContainKey("allOf") },
        )
    }

    @Test
    fun `generateGlobalValuesSchema should not use alias to generate ref to external JSON schemas`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "$EXTERNAL_SCHEMA-alias")
        ))
        val json = generator.generateGlobalValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("allOf").isArray.hasSize(1) },
            { it.node("allOf[0].\$ref").isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE") },
        )
    }

    @Test
    fun `generateGlobalValuesSchema should use relative ref to external JSON schemas when repositories are same`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)))
        val json = generator.generateGlobalValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("allOf").isArray.hasSize(1) },
            { it.node("allOf[0].\$ref").isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE") },
        )
    }

    @Test
    fun `generateGlobalValuesSchema should use relative ref to external JSON schemas when repositories have same host`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, BUNDLES)))
        val json = generator.generateGlobalValuesJsonSchema(chart, null)
        assertThatJson(json).and(
            { it.node("allOf").isArray.hasSize(1) },
            { it.node("allOf[0].\$ref").isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE") },
        )
    }

    @Test
    fun `generateGlobalValuesSchema should use full ref to external JSON schemas when repositories are different`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, INFRA)))
        val json = generator.generateGlobalValuesJsonSchema(chart, null)
        assertThatJson(json).node("allOf[0].\$ref")
            .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$GLOBAL_VALUES_SCHEMA_FILE")
    }

    @Test
    fun `generateGlobalValuesSchema should update generated schema with patch when it is provided`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)))
        val jsonPatch = jsonPatch("""
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/allOf/0/title", "value": "additional value" }
            ]
            """)
        val json = generator.generateGlobalValuesJsonSchema(chart, jsonPatch)
        assertThatJson(json).and(
            { it.node("title").isEqualTo("overridden value") },
            { it.node("allOf[0].title").isEqualTo("additional value") },
            { it.node("allOf[0].\$ref").isString.contains(EXTERNAL_SCHEMA) },
        )
    }

    private fun jsonPatch(content: String) = JsonPatch.fromJson(jsonMapper.readTree(content))
}
