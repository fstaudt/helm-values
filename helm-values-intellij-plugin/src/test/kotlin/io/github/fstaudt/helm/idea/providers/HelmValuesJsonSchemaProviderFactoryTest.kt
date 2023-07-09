package io.github.fstaudt.helm.idea.providers

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.SchemaType.userSchema
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion.SCHEMA_7
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.EXTRA_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.github.fstaudt.helm.idea.initJsonSchemas
import io.github.fstaudt.helm.idea.service.HelmChartService.Companion.JSON_SCHEMAS_DIR
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class HelmValuesJsonSchemaProviderFactoryTest : BasePlatformTestCase() {

    private lateinit var factory: HelmValuesJsonSchemaProviderFactory

    private fun reset() {
        factory = HelmValuesJsonSchemaProviderFactory()
        File(project.baseDir(), JSON_SCHEMAS_DIR).deleteRecursively()
    }

    fun `test - getProviders should return empty list when there is no json schemas directory with helm chart metadata`() {
        reset()
        project.initHelmChart()
        assertThat(factory.getProviders(project)).isEmpty()
    }

    fun `test - getProviders should return aggregated provider for each directory with helm chart metadata`() {
        reset()
        project.initHelmChart()
        project.initJsonSchemas()
        val providers = factory.getProviders(project)
        assertThat(providers).hasSize(2)
        val provider = providers[0]
        assertThat(provider).isInstanceOf(AggregatedJsonSchemaProvider::class.java)
        assertThat(provider.schemaVersion).isEqualTo(SCHEMA_7)
        assertThat(provider.schemaType).isEqualTo(userSchema)
        assertThat(provider.name).isEqualTo("aggregation for chart $CHART_NAME")
        assertThat(provider.schemaFile?.path).endsWith("$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")
    }

    fun `test - getProviders should return extra provider for each directory with helm chart metadata`() {
        reset()
        project.initHelmChart()
        project.initJsonSchemas()
        val providers = factory.getProviders(project)
        assertThat(providers).hasSize(2)
        val provider = providers[1]
        assertThat(provider).isInstanceOf(ExtraJsonSchemaProvider::class.java)
        assertThat(provider.schemaVersion).isEqualTo(SCHEMA_7)
        assertThat(provider.schemaType).isEqualTo(userSchema)
        assertThat(provider.name).isEqualTo("aggregation for packaged chart $CHART_NAME")
        assertThat(provider.schemaFile?.path).endsWith("$JSON_SCHEMAS_DIR/$CHART_NAME/$EXTRA_VALUES_SCHEMA_FILE")
    }
}
