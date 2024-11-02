package io.github.fstaudt.helm.idea

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.Mappers.metadataMapper
import io.github.fstaudt.helm.idea.model.HelmMetadata
import io.github.fstaudt.helm.idea.service.HelmJsonSchemaService.Companion.JSON_SCHEMAS_DIR
import io.github.fstaudt.helm.idea.service.HelmService.Companion.HELM_METADATA_FILE
import io.github.fstaudt.helm.idea.tasks.actions.UpdateHelmMetadataNotificationAction
import io.github.fstaudt.helm.idea.tasks.actions.UpdateRepositoryAllNotificationAction
import java.io.File
import java.time.OffsetDateTime
import java.time.OffsetDateTime.now

class HelmValuesProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val jsonSchemasDir = File(project.baseDir(), JSON_SCHEMAS_DIR)
        if (jsonSchemasDir.isDirectory) {
            val helmMetadata = File(jsonSchemasDir, HELM_METADATA_FILE).takeIf { it.exists() }?.let {
                it.inputStream().use { metadataMapper.readValue(it, HelmMetadata::class.java) }
            }
            val lastUpdate = helmMetadata?.lastUpdate ?: OffsetDateTime.MIN
            if (lastUpdate.isBefore(now().minusDays(1))) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("helm.values.errors")
                    .createNotification(message("tasks.updateRepositoryAll.outdated.title"),
                        message("tasks.updateRepositoryAll.outdated.info"), WARNING)
                    .apply {
                        addAction(UpdateRepositoryAllNotificationAction())
                        addAction(UpdateHelmMetadataNotificationAction())
                    }
                    .notify(project)
            }
        }
    }
}
