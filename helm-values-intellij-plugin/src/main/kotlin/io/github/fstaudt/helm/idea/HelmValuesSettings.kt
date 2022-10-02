package io.github.fstaudt.helm.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepository

@State(name = "io.github.fstaudt.helm.idea.HelmValuesSettings", storages = [Storage("helm.values.xml")])
class HelmValuesSettings : PersistentStateComponent<HelmValuesSettings> {
    companion object {
        val instance: HelmValuesSettings =
            ApplicationManager.getApplication().getService(HelmValuesSettings::class.java)
    }

    var jsonSchemaRepositories: Map<String, JsonSchemaRepository> = emptyMap()

    override fun getState() = this

    override fun loadState(state: HelmValuesSettings) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
