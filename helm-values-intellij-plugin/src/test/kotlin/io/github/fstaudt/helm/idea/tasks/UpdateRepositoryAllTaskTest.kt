package io.github.fstaudt.helm.idea.tasks

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.time.OffsetDateTime

class UpdateRepositoryAllTaskTest : HeavyPlatformTestCase() {
    private lateinit var state: HelmValuesSettings

    private fun reset() {
        state = HelmValuesSettings.instance().state
        state.reset()
        mockkConstructor(GeneralCommandLine::class)
        every { anyConstructed<GeneralCommandLine>().createProcess() } returns mockk<Process>(relaxed = true)
        mockkConstructor(OSProcessHandler::class)
        every { anyConstructed<OSProcessHandler>().waitFor(any()) } returns true
        every { anyConstructed<OSProcessHandler>().exitCode } returns 0
    }

    fun `test - run should update repositories for all charts in project`() {
        reset()
        project.initHelmChart(File(project.baseDir(), CHART_NAME))
        UpdateRepositoryAllTask(project).also {
            it.run(BackgroundableProcessIndicator(it))
        }
        verifyOrder {
            anyConstructed<GeneralCommandLine>().withWorkDirectory(project.baseDir())
            anyConstructed<GeneralCommandLine>().withParameters(*arrayOf("repo", "update"))
            anyConstructed<GeneralCommandLine>().createProcess()
        }
    }

    fun `test - run should update Helm metadata when helm repo update succeeds`() {
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

    fun `test - run should not update Helm metadata when helm repo update fails`() {
        reset()
        every { anyConstructed<OSProcessHandler>().exitCode } returns 1
        project.initHelmChart(File(project.baseDir(), CHART_NAME))
        UpdateRepositoryAllTask(project).also {
            it.run(BackgroundableProcessIndicator(it))
        }
        val helmMetadataFile = File(project.baseDir(), "$JSON_SCHEMAS_DIR/$HELM_METADATA_FILE")
        assertThat(helmMetadataFile).doesNotExist()
    }
}
