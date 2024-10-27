package io.github.fstaudt.helm.idea.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.util.Function
import com.intellij.util.ui.table.TableModelEditor
import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.settings.service.ChartRepositoryService
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import io.github.fstaudt.helm.idea.settings.ui.HelmValuesConfigurable.Companion.FIELD_REQUIRED
import java.net.URI
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty0

class ChartRepositoryEditor : TableModelEditor.DialogItemEditor<ChartRepository> {
    override fun getItemClass() = ChartRepository::class.java
    override fun applyEdited(oldItem: ChartRepository, newItem: ChartRepository) {
        oldItem.applyFrom(newItem).also { it.pushedToHelm = false }
    }

    override fun clone(item: ChartRepository, forInPlaceEditing: Boolean): ChartRepository {
        return if (forInPlaceEditing) item else item.deepCopy().also { it.pushedToHelm = false }
    }

    override fun isUseDialogToAdd() = true
    override fun edit(
        item: ChartRepository,
        mutator: Function<in ChartRepository, out ChartRepository>,
        isAdd: Boolean
    ) {
        val repositories = ChartRepositoryService.instance().list()
        val referenceRepositories = repositories.filter { !it.referenced() && it.name != item.name }.map { it.name }
        lateinit var name: Cell<JBTextField>
        lateinit var url: Cell<JBTextField>
        lateinit var referenced: Cell<JBCheckBox>
        lateinit var referenceRepository: Cell<ComboBox<String>>
        lateinit var password: Cell<JBPasswordField>
        lateinit var username: Cell<JBTextField>
        val mappingItem = item.jsonSchemaRepositoryMapping
        val mappings = JsonSchemaRepositoryMappingService.instance().list()
        val referenceMappings = mappings.filter { !it.referenced() && it.name != item.name }.map { it.name }
        lateinit var jsonSchemaRepositoryMapping: Cell<JBCheckBox>
        lateinit var mappingBaseUri: Cell<JBTextField>
        lateinit var mappingReferenced: Cell<JBCheckBox>
        lateinit var mappingReferenceRepository: Cell<ComboBox<String>>
        lateinit var mappingPassword: Cell<JBPasswordField>
        lateinit var mappingUsername: Cell<JBTextField>
        lateinit var mappingValuesSchemaFile: Cell<JBTextField>
        lateinit var mappingGlobalValuesSchemaFile: Cell<JBTextField>
        val panel = panel {
            rowWithTextFieldForProperty(item::name) { cell ->
                cell.focused().also { name = it }
            }
            // Chart repository
            rowWithTextFieldForProperty(item::url) { cell ->
                cell.validationOnInput {
                    error(message("settings.charts.url.errors.invalid")).takeIf {
                        runCatching { !URI(cell.component.text).isAbsolute }.getOrElse { true }
                    }
                }.also { url = it }
            }
            row(message("settings.charts.referenced.label")) {
                checkBox("").let { cell ->
                    cell.bindSelected(
                        { item.referenced() },
                        { if (!it) referenceRepository.component.selectedItem = "" }
                    )
                }.also { referenced = it }
            }.comment(message("settings.charts.referenced.comment"))
            rowWithComboBoxForProperty(item::referenceRepository, referenceRepositories) { cell ->
                cell.component.addActionListener {
                    username.component.text = ""
                    password.component.text = ""
                    repositories.firstOrNull { it.name == "${cell.component.selectedItem}" }?.let {
                        username.component.text = it.username
                        password.component.text = it.password
                    }
                }
                cell.visibleIf(referenced.selected).also { referenceRepository = it }
            }
            rowWithTextFieldForProperty(item::username) { cell ->
                cell.enabledIf(referenced.selected.not()).also { username = it }
            }
            rowWithPasswordFieldForProperty(item::password) { cell ->
                cell.enabledIf(referenced.selected.not()).also { password = it }
            }
            // JSON schema repository mapping
            separator()
            row(message("settings.charts.jsonSchemaRepository.label")) {
                checkBox("").let { cell ->
                    cell.bindSelected(
                        { mappingItem.name.isNotBlank() },
                        {
                            if (it) {
                                mappingItem.name = "@${name.component.text}"
                            } else {
                                mappingItem.name = ""
                                mappingBaseUri.component.text = ""
                                mappingReferenced.component.isSelected = false
                                mappingReferenceRepository.component.selectedItem = ""
                                mappingPassword.component.text = ""
                                mappingUsername.component.text = ""
                                mappingValuesSchemaFile.component.text = VALUES_SCHEMA_FILE
                                mappingGlobalValuesSchemaFile.component.text = GLOBAL_VALUES_SCHEMA_FILE
                            }
                        }
                    )
                }.also { jsonSchemaRepositoryMapping = it }
            }.comment(message("settings.charts.jsonSchemaRepository.comment"))
            rowWithTextFieldForProperty(mappingItem::baseUri, "settings.mappings") { cell ->
                cell.validationOnInput {
                    error(message("settings.mappings.baseUri.errors.invalid")).takeIf {
                        runCatching { !URI(cell.component.text).isAbsolute }.getOrElse { true }
                    }
                }.also { mappingBaseUri = it }
            }.visibleIf(jsonSchemaRepositoryMapping.selected)
            row(message("settings.mappings.referenced.label")) {
                checkBox("").let { cell ->
                    cell.bindSelected(
                        { mappingItem.referenced() },
                        { if (!it) mappingReferenceRepository.component.selectedItem = "" }
                    )
                }.also { mappingReferenced = it }
                comment(message("settings.mappings.referenced.comment"))
                visibleIf(jsonSchemaRepositoryMapping.selected)
            }
            rowWithComboBoxForProperty(mappingItem::referenceRepositoryMapping, referenceMappings, "settings.mappings") { cell ->
                cell.component.addActionListener {
                    mappingUsername.component.text = ""
                    mappingPassword.component.text = ""
                    mappingValuesSchemaFile.component.text = VALUES_SCHEMA_FILE
                    mappingGlobalValuesSchemaFile.component.text = GLOBAL_VALUES_SCHEMA_FILE
                    mappings.firstOrNull { it.name == "${cell.component.selectedItem}" }?.let {
                        mappingUsername.component.text = it.username
                        mappingPassword.component.text = it.password
                        mappingValuesSchemaFile.component.text = it.valuesSchemaFile
                        mappingGlobalValuesSchemaFile.component.text = it.globalValuesSchemaFile
                    }
                }
                cell.visibleIf(jsonSchemaRepositoryMapping.selected.and(mappingReferenced.selected))
                    .also { mappingReferenceRepository = it }
            }.visibleIf(jsonSchemaRepositoryMapping.selected)
            rowWithTextFieldForProperty(mappingItem::username, "settings.mappings") { cell ->
                cell.enabledIf(mappingReferenced.selected.not()).also { mappingUsername = it }
            }.visibleIf(jsonSchemaRepositoryMapping.selected)
            rowWithPasswordFieldForProperty(mappingItem::password, "settings.mappings") { cell ->
                cell.enabledIf(mappingReferenced.selected.not()).also { mappingPassword = it }
            }.visibleIf(jsonSchemaRepositoryMapping.selected)
            rowWithTextFieldForProperty(mappingItem::valuesSchemaFile, "settings.mappings") { cell ->
                cell.enabledIf(mappingReferenced.selected.not()).also { mappingValuesSchemaFile = it }
            }.visibleIf(jsonSchemaRepositoryMapping.selected)
            rowWithTextFieldForProperty(mappingItem::globalValuesSchemaFile, "settings.mappings") { cell ->
                cell.enabledIf(mappingReferenced.selected.not()).also { mappingGlobalValuesSchemaFile = it }
            }.visibleIf(jsonSchemaRepositoryMapping.selected)
        }
        dialog(title = message("settings.charts.dialog.title"), panel = panel, ok = {
            val errors = mutableListOf<ValidationInfo>()
            if (name.component.text.isBlank()) {
                errors += ValidationInfoBuilder(name.component).error(message(FIELD_REQUIRED))
            }
            if (url.component.text.isBlank()) {
                errors += ValidationInfoBuilder(url.component).error(message(FIELD_REQUIRED))
            }
            if (referenceRepository.component.item.isBlank() && referenceRepository.component.isVisible) {
                errors += ValidationInfoBuilder(referenceRepository.component).error(message(FIELD_REQUIRED))
            }
            return@dialog errors
        }).show()
        mutator.`fun`(item)
    }

    private fun Panel.rowWithTextFieldForProperty(
        prop: KMutableProperty0<String>,
        keyPrefix: String = "settings.charts",
        textFieldFn: (Cell<JBTextField>) -> Cell<JBTextField> = { it }
    ): Row {
        return row(message("$keyPrefix.${prop.name}.label")) {
            textField().forProperty(prop, keyPrefix, textFieldFn)
        }
    }

    private fun Panel.rowWithPasswordFieldForProperty(
        prop: KMutableProperty0<String>,
        keyPrefix: String = "settings.charts",
        passwordFieldFn: (Cell<JBPasswordField>) -> Cell<JBPasswordField> = { it }
    ): Row {
        return row(message("$keyPrefix.${prop.name}.label")) {
            cell(JBPasswordField()).forProperty(prop, keyPrefix, passwordFieldFn)
        }
    }

    private fun Panel.rowWithComboBoxForProperty(
        prop: KMutableProperty0<String>,
        items: List<String>,
        keyPrefix: String = "settings.charts",
        comboboxFn: (Cell<ComboBox<String>>) -> Cell<ComboBox<String>> = { it }
    ): Row {
        return row(message("$keyPrefix.${prop.name}.label")) {
            comboBox(listOf("") + items)
                .accessibleName(prop.name)
                .bindItem(prop.toNullableProperty())
                .columns(COLUMNS_LARGE)
                .comment(message("$keyPrefix.${prop.name}.comment"))
                .let { comboboxFn(it) }
        }
    }

    private fun <T : JTextField> Cell<T>.forProperty(
        prop: KMutableProperty0<String>,
        keyPrefix: String = "settings.charts",
        textFieldFn: (Cell<T>) -> Cell<T> = { it }
    ): Cell<T> {
        return accessibleName(prop.name)
            .bindText(prop)
            .columns(COLUMNS_LARGE)
            .comment(message("$keyPrefix.${prop.name}.comment"))
            .let { textFieldFn(it) }
    }
}
