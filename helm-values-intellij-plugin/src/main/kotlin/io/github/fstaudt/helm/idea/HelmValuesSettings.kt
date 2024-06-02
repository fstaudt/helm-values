package io.github.fstaudt.helm.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType.DISABLED
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import io.github.fstaudt.helm.idea.settings.model.ChartRepositoryState
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryState

@Service
@State(name = "io.github.fstaudt.helm.idea.HelmValuesSettings",
    storages = [Storage("helm.values.xml", roamingType = DISABLED)])
class HelmValuesSettings : PersistentStateComponent<HelmValuesSettings> {
    companion object {
        const val HELM_BINARY = "helm"
        val instance: HelmValuesSettings =
            ApplicationManager.getApplication().getService(HelmValuesSettings::class.java)
    }

    var helmBinaryPath: String = HELM_BINARY
    var jsonSchemaRepositories: Map<String, JsonSchemaRepositoryState> = emptyMap()
    var chartRepositories: Map<String, ChartRepositoryState> = emptyMap()

    override fun getState() = this

    override fun loadState(state: HelmValuesSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun reset() {
        helmBinaryPath = HELM_BINARY
        jsonSchemaRepositories = emptyMap()
        chartRepositories = emptyMap()
    }
}
