package io.github.fstaudt.helm

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonpatch.JsonPatch
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_DESCRIPTION
import io.github.fstaudt.helm.JsonSchemaGenerator.Companion.GLOBAL_VALUES_TITLE
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
        generator = JsonSchemaGenerator(repositoryMappings, APPS)
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
        assertThatJson(json).and({
            it.node("\$schema").isEqualTo(SCHEMA_VERSION)
            it.node("\$id").isEqualTo("$BASE_CHART_URL/$VALUES_SCHEMA_FILE")
            it.node("x-generated-by").isEqualTo(GENERATOR_LABEL)
            it.node("x-generated-at").isString.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}(:\\d{2}){1,2}Z")
            it.node("title").isEqualTo("Configuration for chart $APPS/$CHART_NAME:$CHART_VERSION")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("properties").isObject.containsKey(EXTERNAL_SCHEMA)
            it.node("properties").isObject.doesNotContainKey(EMBEDDED_SCHEMA)
        })
    }

    @Test
    fun `generateValuesJsonSchema should ignore dependencies without version or repository`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, null, "no-repository"),
            ChartDependency(EXTERNAL_SCHEMA, null, APPS, "no-version"),
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties").isObject.doesNotContainKeys("no-repository", "no-version")
    }

    @Test
    fun `generateValuesJsonSchema should use alias as property names`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "alias")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and({
            it.node("properties").isObject.doesNotContainKey(EXTERNAL_SCHEMA)
            it.node("properties.alias.\$ref").isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
        })
    }

    @Test
    fun `generateValuesJsonSchema should disable additional and unevaluated properties`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).and({
            it.node("additionalProperties").isBoolean.isFalse
            it.node("properties.global.unevaluatedProperties").isBoolean.isFalse
        })
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas when repositories are same`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.\$ref")
            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas when repositories have same host`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, BUNDLES)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.\$ref")
            .isEqualTo("../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.\$ref")
            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `generateValuesJsonSchema should use alias when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA", alias = "alias")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.alias.\$ref")
            .isEqualTo("../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `generateValuesJsonSchema should use full ref to external JSON schemas when repositories are different`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, INFRA)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.\$ref")
            .isEqualTo("$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION/$VALUES_SCHEMA_FILE")
    }

    @Test
    fun `generateValuesJsonSchema should set property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "deps.$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.deps.properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `generateValuesJsonSchema should set property for dependency condition in allOf when it conflicts with $ref`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.allOf[0]").isObject.containsOnlyKeys("\$ref")
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA").node("unevaluatedProperties").isBoolean.isFalse
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `generateValuesJsonSchema should set properties for dependency condition when condition contains comma`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS,
                condition = "deps.$EXTERNAL_SCHEMA.enabled,other.$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
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
    fun `generateValuesJsonSchema should set property for dependency condition when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA",
                condition = "deps.$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.deps.properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title").isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `generateValuesJsonSchema should set property for dependency condition for third-party dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, THIRDPARTY, condition = "$EXTERNAL_SCHEMA.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.$EXTERNAL_SCHEMA.properties.enabled").and({
            it.node("title")
                .isEqualTo("Enable $EXTERNAL_SCHEMA dependency ($THIRDPARTY/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `generateValuesJsonSchema should set property for dependency condition for dependencies without version or repository`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, null, "no-repository", "no-repository.enabled"),
            ChartDependency(EXTERNAL_SCHEMA, null, APPS, "no-version", "no-version.enabled"),
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.no-repository.properties.enabled").and({
            it.node("title").isEqualTo("Enable no-repository dependency ($EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
        assertThatJson(json).node("properties.no-version.properties.enabled").and({
            it.node("title").isEqualTo("Enable no-version dependency ($APPS/$EXTERNAL_SCHEMA)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `generateValuesJsonSchema should use alias to document property for dependency condition`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "alias", "deps.alias.enabled")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.deps.properties.alias.properties.enabled").and({
            it.node("title").isEqualTo("Enable alias dependency ($APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION)")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("type").isEqualTo("boolean")
        })
    }

    @Test
    fun `generateValuesJsonSchema should update generated schema with patch when it is provided`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)
        ))
        val jsonPatch = jsonPatch("""
            [
              { "op": "replace", "path": "/title", "value": "overridden value" },
              { "op": "add", "path": "/properties/$EXTERNAL_SCHEMA/title", "value": "additional value" }
            ]
            """)
        val json = generator.generateValuesJsonSchema(chart, jsonPatch)
        assertThatJson(json).and({
            it.node("title").isEqualTo("overridden value")
            it.node("properties.$EXTERNAL_SCHEMA.title").isEqualTo("additional value")
            it.node("properties.$EXTERNAL_SCHEMA").isObject.containsKey("\$ref")
        })
    }

    @Test
    fun `generateValuesJsonSchema should generate global values with dependencies in mapped repositories`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS),
            ChartDependency(EMBEDDED_SCHEMA, EMBEDDED_VERSION, THIRDPARTY),
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            val externalSchemaRef = "../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
            it.node("unevaluatedProperties").isBoolean.isFalse
            it.node("allOf").isArray.hasSize(3)
            it.node("allOf[0].\$ref").isEqualTo("$externalSchemaRef/$VALUES_SCHEMA_FILE#/properties/global")
            it.node("allOf[1].\$ref").isEqualTo("$externalSchemaRef/$GLOBAL_VALUES_SCHEMA_FILE")
            it.node("allOf[2].title").isEqualTo("$GLOBAL_VALUES_TITLE $CHART_NAME:$CHART_VERSION")
            it.node("allOf[2].description").isString
                .contains(GLOBAL_VALUES_DESCRIPTION)
                .contains("$APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
                .doesNotContain("$THIRDPARTY/$EMBEDDED_SCHEMA:$EMBEDDED_VERSION")
            it.node("allOf[2].x-intellij-html-description").isString
                .contains(GLOBAL_VALUES_DESCRIPTION)
                .contains("$APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
                .contains("$BASE_URL/$APPS_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION")
                .doesNotContain("$THIRDPARTY/$EMBEDDED_SCHEMA:$EMBEDDED_VERSION")
        })
    }

    @Test
    fun `generateValuesJsonSchema should generate empty global properties when there are no dependencies`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            it.node("unevaluatedProperties").isBoolean.isFalse
            it.isObject.doesNotContainKey("allOf")
        })
    }

    @Test
    fun `generateValuesJsonSchema should not use alias to generate ref to external JSON schemas for global`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS, "alias")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            val externalSchemaRef = "../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
            it.node("allOf").isArray.hasSize(3)
            it.node("allOf[0].\$ref").isEqualTo("$externalSchemaRef/$VALUES_SCHEMA_FILE#/properties/global")
            it.node("allOf[1].\$ref").isEqualTo("$externalSchemaRef/$GLOBAL_VALUES_SCHEMA_FILE")
            it.node("allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
        })
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas for global when repositories are same`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, APPS)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            val externalSchemaRef = "../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
            it.node("allOf").isArray.hasSize(3)
            it.node("allOf[0].\$ref").isEqualTo("$externalSchemaRef/$VALUES_SCHEMA_FILE#/properties/global")
            it.node("allOf[1].\$ref").isEqualTo("$externalSchemaRef/$GLOBAL_VALUES_SCHEMA_FILE")
            it.node("allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
        })
    }

    @Test
    fun `generateValuesJsonSchema should use relative ref to external JSON schemas for global when repositories have same host`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, BUNDLES)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            val externalSchemaRef = "../../../$BUNDLES_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
            it.node("allOf").isArray.hasSize(3)
            it.node("allOf[0].\$ref").isEqualTo("$externalSchemaRef/$VALUES_SCHEMA_FILE#/properties/global")
            it.node("allOf[1].\$ref").isEqualTo("$externalSchemaRef/$GLOBAL_VALUES_SCHEMA_FILE")
            it.node("allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
        })
    }

    @Test
    fun `generateValuesJsonSchema should use full ref to external JSON schemas for global when repositories are different`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, INFRA)
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            val externalSchemaRef = "$INFRA_REPOSITORY_URL/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
            it.node("allOf").isArray.hasSize(3)
            it.node("allOf[0].\$ref").isEqualTo("$externalSchemaRef/$VALUES_SCHEMA_FILE#/properties/global")
            it.node("allOf[1].\$ref").isEqualTo("$externalSchemaRef/$GLOBAL_VALUES_SCHEMA_FILE")
            it.node("allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
        })
    }

    @Test
    fun `generateValuesJsonSchema should add refs in global when dependency is stored locally`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION, listOf(
            ChartDependency(EXTERNAL_SCHEMA, EXTERNAL_VERSION, "file://../$EXTERNAL_SCHEMA")
        ))
        val json = generator.generateValuesJsonSchema(chart, null)
        assertThatJson(json).node("properties.global").and({
            val externalSchemaRef = "../../$EXTERNAL_SCHEMA/$EXTERNAL_VERSION"
            it.node("allOf").isArray.hasSize(3)
            it.node("allOf[0].\$ref").isEqualTo("$externalSchemaRef/$VALUES_SCHEMA_FILE#/properties/global")
            it.node("allOf[1].\$ref").isEqualTo("$externalSchemaRef/$GLOBAL_VALUES_SCHEMA_FILE")
            it.node("allOf[2].title").isString.startsWith(GLOBAL_VALUES_TITLE)
            it.node("allOf[2].description").isString
                .contains("$APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
            it.node("allOf[2].x-intellij-html-description").isString
                .contains("$BASE_URL/$APPS_PATH/$EXTERNAL_SCHEMA/$EXTERNAL_VERSION")
                .contains("$APPS/$EXTERNAL_SCHEMA:$EXTERNAL_VERSION")
        })
    }

    @Test
    fun `generateExtraValuesJsonSchema should generate JSON schema with references to aggregated schema`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = generator.generateExtraValuesJsonSchema(chart, null)
        assertThatJson(json).and({
            it.node("\$schema").isEqualTo(SCHEMA_VERSION)
            it.node("\$id").isEqualTo("$BASE_CHART_URL/$EXTRA_VALUES_SCHEMA_FILE")
            it.node("x-generated-by").isEqualTo(GENERATOR_LABEL)
            it.node("x-generated-at").isString.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}(:\\d{2}){1,2}Z")
            it.node("title").isEqualTo("Extra configuration for packaged chart $APPS/$CHART_NAME:$CHART_VERSION")
            it.node("description").isEqualTo("\\n\\\\n")
            it.node("properties.global.\$ref").isEqualTo("$AGGREGATED_SCHEMA_FILE#/properties/global")
            it.node("properties.$CHART_NAME.\$ref").isEqualTo(AGGREGATED_SCHEMA_FILE)
        })
    }

    @Test
    fun `generateExtraValuesJsonSchema should disable additional properties`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val json = generator.generateExtraValuesJsonSchema(chart, null)
        assertThatJson(json).node("additionalProperties").isBoolean.isFalse
    }

    @Test
    fun `generateExtraValuesJsonSchema should update generated JSON schema with patch when it is provided`() {
        val chart = Chart("v2", CHART_NAME, CHART_VERSION)
        val jsonPatch = jsonPatch("""
            [
              { "op": "replace", "path": "/title", "value": "overridden value" }
            ]
            """)
        val json = generator.generateExtraValuesJsonSchema(chart, jsonPatch)
        assertThatJson(json).node("title").isEqualTo("overridden value")
    }

    private fun jsonPatch(content: String) = JsonPatch.fromJson(jsonMapper.readTree(content))
}
