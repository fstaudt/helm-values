package io.github.fstaudt.helm.idea.settings.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.table.TableModelEditor
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.HelmValuesSettings.Companion.HELM_BINARY
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import javax.swing.JTable
import javax.swing.JTextField
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1

/**
 * Provides controller functionality for application settings.
 */
class HelmValuesConfigurable : BoundSearchableConfigurable(message("name"), "helm.values") {
    private val state = HelmValuesSettings.instance.state
    private val jsonSchemaRepositoryMappingService = JsonSchemaRepositoryMappingService.instance
    private val tableEditor = TableModelEditor(
        arrayOf(
            Column(JsonSchemaRepositoryMapping::name, 40),
            Column(JsonSchemaRepositoryMapping::baseUri, 150),
            Column(JsonSchemaRepositoryMapping::valuesSchemaFile, 50),
            Column(JsonSchemaRepositoryMapping::globalValuesSchemaFile, 60),
            BooleanColumn(JsonSchemaRepositoryMapping::secured)
        ),
        JsonSchemaRepositoryMappingEditor(),
        message("settings.mappings.none")
    ).disableUpDownActions()
    private lateinit var helmBinaryPath: Cell<JBTextField>

    override fun createPanel(): DialogPanel {
        tableEditor.reset(jsonSchemaRepositoryMappingService.list())
        return panel {
            rowWithTextFieldForProperty(state::helmBinaryPath) { cell ->
                cell.focused().also { helmBinaryPath = it }
            }
            row {
                cell(tableEditor.createComponent())
                    .horizontalAlign(HorizontalAlign.FILL)
                    .verticalAlign(VerticalAlign.FILL)
                    .label(message("settings.mappings.label"), LabelPosition.TOP)
            }.resizableRow()
        }
    }

    override fun isModified(): Boolean {
        return tableEditor.model.items.sortedBy { it.name } != jsonSchemaRepositoryMappingService.list()
                || helmBinaryPath.component.text.trimOrElse(HELM_BINARY) != state.helmBinaryPath
    }

    override fun apply() {
        jsonSchemaRepositoryMappingService.update(tableEditor.model.items)
        state.helmBinaryPath = helmBinaryPath.component.text.trimOrElse(HELM_BINARY)
        reset()
    }

    override fun reset() {
        tableEditor.reset(jsonSchemaRepositoryMappingService.list())
        helmBinaryPath.component.text = state.helmBinaryPath
    }

    private class Column<T, C>(
        private val field: KMutableProperty1<T, C>,
        private val preferredWidth: Int? = null,
    ) : TableModelEditor.EditableColumnInfo<T, C>() {
        override fun getName() = message("settings.mappings.${field.name}.title")
        override fun getPreferredStringValue() = preferredWidth?.let { "".padEnd(it) }
        override fun valueOf(item: T): C = field.get(item)
        override fun setValue(item: T, value: C) {
            field.set(item, value)
        }

        override fun isCellEditable(item: T) = false
    }

    private class BooleanColumn<R>(
        private val function: KFunction<Boolean>,
    ) : TableModelEditor.EditableColumnInfo<R, Boolean>() {
        override fun getColumnClass() = Boolean::class.java
        override fun getWidth(table: JTable) = 50
        override fun getName() = message("settings.mappings.${function.name}.title")
        override fun valueOf(item: R): Boolean = function.call(item)
        override fun isCellEditable(item: R) = false
    }

    private fun Panel.rowWithTextFieldForProperty(
        prop: KMutableProperty0<String>,
        textFieldFn: (Cell<JBTextField>) -> Cell<JBTextField> = { it },
    ): Row {
        return row(message("settings.${prop.name}.label")) {
            textField().forProperty(prop, textFieldFn)
        }
    }

    private fun <T : JTextField> Cell<T>.forProperty(
        prop: KMutableProperty0<String>,
        textFieldFn: (Cell<T>) -> Cell<T> = { it },
    ): Cell<T> {
        return accessibleName(prop.name)
            .bindText(prop)
            .columns(COLUMNS_LARGE)
            .comment(message("settings.${prop.name}.comment"))
            .let { textFieldFn(it) }
    }

    private fun String.trimOrElse(default: String) = trim().takeIf { it.isNotEmpty() } ?: default
}
