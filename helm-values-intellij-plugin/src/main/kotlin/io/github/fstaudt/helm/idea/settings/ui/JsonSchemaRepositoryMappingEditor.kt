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
import com.intellij.ui.layout.not
import com.intellij.util.Function
import com.intellij.util.ui.table.TableModelEditor
import io.github.fstaudt.helm.JsonSchemaConstants.GLOBAL_VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.JsonSchemaConstants.VALUES_SCHEMA_FILE
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import io.github.fstaudt.helm.idea.settings.ui.HelmValuesConfigurable.Companion.FIELD_REQUIRED
import java.net.URI
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty0

class JsonSchemaRepositoryMappingEditor : TableModelEditor.DialogItemEditor<JsonSchemaRepositoryMapping> {
    override fun getItemClass() = JsonSchemaRepositoryMapping::class.java
    override fun applyEdited(oldItem: JsonSchemaRepositoryMapping, newItem: JsonSchemaRepositoryMapping) {
        oldItem.applyFrom(newItem)
    }

    override fun clone(item: JsonSchemaRepositoryMapping, forInPlaceEditing: Boolean): JsonSchemaRepositoryMapping {
        return if (forInPlaceEditing) item else item.copy()
    }

    override fun isUseDialogToAdd() = true
    override fun edit(
        item: JsonSchemaRepositoryMapping,
        mutator: Function<in JsonSchemaRepositoryMapping, out JsonSchemaRepositoryMapping>,
        isAdd: Boolean
    ) {
        val mappings = JsonSchemaRepositoryMappingService.instance.list()
        val referenceMappings = mappings.filter { !it.referenced() && it.name != item.name }.map { it.name }
        lateinit var name: Cell<JBTextField>
        lateinit var baseUri: Cell<JBTextField>
        lateinit var referenced: Cell<JBCheckBox>
        lateinit var referenceRepositoryMapping: Cell<ComboBox<String>>
        lateinit var password: Cell<JBPasswordField>
        lateinit var username: Cell<JBTextField>
        lateinit var valuesSchemaFile: Cell<JBTextField>
        lateinit var globalValuesSchemaFile: Cell<JBTextField>
        val panel = panel {
            rowWithTextFieldForProperty(item::name) { cell ->
                cell.focused().also { name = it }
            }
            rowWithTextFieldForProperty(item::baseUri) { cell ->
                cell.validationOnInput {
                    error(message("settings.mappings.baseUri.errors.invalid")).takeIf {
                        runCatching { !URI(cell.component.text).isAbsolute }.getOrElse { true }
                    }
                }.also { baseUri = it }
            }
            row(message("settings.mappings.referenced.label")) {
                checkBox("").let { cell ->
                    cell.bindSelected(
                        { item.referenced() },
                        { if (!it) referenceRepositoryMapping.component.selectedItem = "" }
                    )
                }.also { referenced = it }
            }.comment(message("settings.mappings.referenced.comment"))
            rowWithComboBoxForProperty(item::referenceRepositoryMapping, referenceMappings) { cell ->
                cell.component.addActionListener {
                    username.component.text = ""
                    password.component.text = ""
                    valuesSchemaFile.component.text = VALUES_SCHEMA_FILE
                    globalValuesSchemaFile.component.text = GLOBAL_VALUES_SCHEMA_FILE
                    mappings.firstOrNull { it.name == "${cell.component.selectedItem}" }?.let {
                        username.component.text = it.username
                        password.component.text = it.password
                        valuesSchemaFile.component.text = it.valuesSchemaFile
                        globalValuesSchemaFile.component.text = it.globalValuesSchemaFile
                    }
                }
                cell.visibleIf(referenced.selected).also { referenceRepositoryMapping = it }
            }
            rowWithTextFieldForProperty(item::username) { cell ->
                cell.enabledIf(referenced.selected.not()).also { username = it }
            }
            rowWithPasswordFieldForProperty(item::password) { cell ->
                cell.enabledIf(referenced.selected.not()).also { password = it }
            }
            rowWithTextFieldForProperty(item::valuesSchemaFile) { cell ->
                cell.enabledIf(referenced.selected.not()).also { valuesSchemaFile = it }
            }
            rowWithTextFieldForProperty(item::globalValuesSchemaFile) { cell ->
                cell.enabledIf(referenced.selected.not()).also { globalValuesSchemaFile = it }
            }
        }
        dialog(title = message("settings.mappings.dialog.title"), panel = panel, ok = {
            val errors = mutableListOf<ValidationInfo>()
            if (name.component.text.isBlank()) {
                errors += ValidationInfoBuilder(name.component).error(message(FIELD_REQUIRED))
            }
            if (baseUri.component.text.isBlank()) {
                errors += ValidationInfoBuilder(baseUri.component).error(message(FIELD_REQUIRED))
            }
            if (referenceRepositoryMapping.component.item.isBlank() && referenceRepositoryMapping.component.isVisible) {
                errors += ValidationInfoBuilder(referenceRepositoryMapping.component).error(message(FIELD_REQUIRED))
            }
            return@dialog errors
        }).show()
        mutator.`fun`(item)
    }

    private fun Panel.rowWithTextFieldForProperty(
        prop: KMutableProperty0<String>,
        textFieldFn: (Cell<JBTextField>) -> Cell<JBTextField> = { it }
    ): Row {
        return row(message("settings.mappings.${prop.name}.label")) {
            textField().forProperty(prop, textFieldFn)
        }
    }

    private fun Panel.rowWithPasswordFieldForProperty(
        prop: KMutableProperty0<String>,
        passwordFieldFn: (Cell<JBPasswordField>) -> Cell<JBPasswordField> = { it }
    ): Row {
        return row(message("settings.mappings.${prop.name}.label")) {
            cell(JBPasswordField()).forProperty(prop, passwordFieldFn)
        }
    }

    private fun Panel.rowWithComboBoxForProperty(
        prop: KMutableProperty0<String>,
        items: List<String>,
        comboboxFn: (Cell<ComboBox<String>>) -> Cell<ComboBox<String>> = { it }
    ): Row {
        return row(message("settings.mappings.${prop.name}.label")) {
            comboBox(listOf("") + items)
                .accessibleName(prop.name)
                .bindItem(prop.toNullableProperty())
                .columns(COLUMNS_LARGE)
                .comment(message("settings.mappings.${prop.name}.comment"))
                .let { comboboxFn(it) }
        }
    }

    private fun <T : JTextField> Cell<T>.forProperty(
        prop: KMutableProperty0<String>,
        textFieldFn: (Cell<T>) -> Cell<T> = { it }
    ): Cell<T> {
        return accessibleName(prop.name)
            .bindText(prop)
            .columns(COLUMNS_LARGE)
            .comment(message("settings.mappings.${prop.name}.comment"))
            .let { textFieldFn(it) }
    }
}
