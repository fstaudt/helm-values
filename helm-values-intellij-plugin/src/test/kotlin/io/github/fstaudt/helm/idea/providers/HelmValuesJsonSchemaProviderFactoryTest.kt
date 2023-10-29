package io.github.fstaudt.helm.idea.providers

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.SchemaType.userSchema
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion.SCHEMA_7
import io.github.fstaudt.helm.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.github.fstaudt.helm.idea.initJsonSchemas
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.JSON_SCHEMAS_DIR
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

class HelmValuesJsonSchemaProviderFactoryTest : BasePlatformTestCase() {

    private lateinit var factory: HelmValuesJsonSchemaProviderFactory

    private class MockVirtualFile(
        private val path: Path = File("values.yaml").toPath(),
        private val parent: VirtualFile? = null,
    ) : StubVirtualFile() {
        override fun getName() = path.name
        override fun getPath() = "$path"
        override fun getParent() = parent
        override fun toNioPath() = path
    }

    private class TgzVirtualFile(
        private val fileName: String = "values.yaml",
        private val parent: VirtualFile? = null,
    ) : StubVirtualFile() {
        override fun getName() = fileName
        override fun getPath() = fileName
        override fun getParent() = parent
        override fun toNioPath(): Path {
            throw UnsupportedOperationException()
        }
    }

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
        assertThat(providers).hasSize(1)
        val provider = providers[0]
        assertThat(provider).isInstanceOf(AggregatedJsonSchemaProvider::class.java)
        assertThat(provider.schemaVersion).isEqualTo(SCHEMA_7)
        assertThat(provider.schemaType).isEqualTo(userSchema)
        assertThat(provider.name).isEqualTo("aggregation for chart $CHART_NAME")
        assertThat(provider.schemaFile?.path).endsWith("$JSON_SCHEMAS_DIR/$CHART_NAME/$AGGREGATED_SCHEMA_FILE")
        assertThat(provider.isAvailable(MockVirtualFile(parent = MockVirtualFile(project.baseDir().toPath())))).isTrue
        assertThat(provider.isAvailable(MockVirtualFile(
            parent = MockVirtualFile(project.baseDir().toPath()),
            path = File("packaged-values.yaml").toPath()
        ))).isFalse
        assertThat(provider.isAvailable(MockVirtualFile(
            parent = MockVirtualFile(project.baseDir().toPath()),
            path = File("other.yaml").toPath()
        ))).isFalse
        assertThat(provider.isAvailable(MockVirtualFile(parent = MockVirtualFile(File(CHART_NAME).toPath())))).isFalse
        assertThat(provider.isAvailable(MockVirtualFile(parent = null))).isFalse
        assertThat(provider.isAvailable(TgzVirtualFile(parent = TgzVirtualFile("chart")))).isFalse
    }
}
