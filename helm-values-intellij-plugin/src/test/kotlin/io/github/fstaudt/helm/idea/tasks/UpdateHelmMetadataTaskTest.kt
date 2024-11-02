package io.github.fstaudt.helm.idea.tasks

import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.testFramework.HeavyPlatformTestCase
import io.github.fstaudt.helm.idea.CHART_NAME
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.Mappers.metadataMapper
import io.github.fstaudt.helm.idea.baseDir
import io.github.fstaudt.helm.idea.initHelmChart
import io.github.fstaudt.helm.idea.model.HelmMetadata
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.JSON_SCHEMAS_DIR
import io.github.fstaudt.helm.idea.service.HelmService.Companion.HELM_METADATA_FILE
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.time.OffsetDateTime

class UpdateHelmMetadataTaskTest : HeavyPlatformTestCase() {
    private lateinit var state: HelmValuesSettings

    private fun reset() {
        state = HelmValuesSettings.instance().state
        state.reset()
    }

    fun `test - run should update Helm metadata`() {
        reset()
        val beforeUpdate = OffsetDateTime.now()
        project.initHelmChart(File(project.baseDir(), CHART_NAME))
        UpdateRepositoryAllTask(project).also {
            it.run(BackgroundableProcessIndicator(it))
        }
        val helmMetadataFile = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$HELM_METADATA_FILE")
        assertThat(helmMetadataFile).isFile.exists()
        val helmMetadata = helmMetadataFile.inputStream().use {
            metadataMapper.readValue(it, HelmMetadata::class.java)
        }
        assertThat(helmMetadata.lastUpdate).isAfterOrEqualTo(beforeUpdate)
    }
}
