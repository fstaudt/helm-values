package io.github.fstaudt.helm.idea.settings.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.table.TableModelEditor
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.JsonSchemaRepositoryMappingService
import javax.swing.JTable
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1

/**
 * Provides controller functionality for application settings.
 */
@Suppress("UnstableApiUsage")
class HelmValuesConfigurable : BoundSearchableConfigurable(message("name"), "helm.values") {
    private val jsonSchemaRepositoryMappingService = JsonSchemaRepositoryMappingService.instance
    private val tableEditor = TableModelEditor(
        arrayOf(
            Column(JsonSchemaRepositoryMapping::name),
            Column(JsonSchemaRepositoryMapping::baseUri),
            BooleanColumn(JsonSchemaRepositoryMapping::secured)
        ),
        JsonSchemaRepositoryMappingEditor(),
        message("settings.mappings.none")
    )

    override fun createPanel(): DialogPanel {
        tableEditor.reset(jsonSchemaRepositoryMappingService.list())
        return panel {
            row {
                cell(tableEditor.createComponent())
                    .horizontalAlign(HorizontalAlign.FILL)
                    .verticalAlign(VerticalAlign.FILL)
                    .label(message("settings.mappings.label"), LabelPosition.TOP)
            }.resizableRow()
        }
    }

    override fun isModified(): Boolean {
        return tableEditor.model.items.sortedBy { it.name } !=
                jsonSchemaRepositoryMappingService.list().sortedBy { it.name }
    }

    override fun apply() {
        jsonSchemaRepositoryMappingService.update(tableEditor.model.items)
        reset()
    }

    override fun reset() {
        tableEditor.reset(jsonSchemaRepositoryMappingService.list())
    }

    private class Column<T, C>(private val field: KMutableProperty1<T, C>) :
        TableModelEditor.EditableColumnInfo<T, C>() {
        override fun getName() = message("settings.mappings.${field.name}.title")
        override fun valueOf(item: T): C = field.get(item)
        override fun setValue(item: T, value: C) {
            field.set(item, value)
        }

        override fun isCellEditable(item: T) = false
    }

    private class BooleanColumn<R>(private val function: KFunction<Boolean>) :
        TableModelEditor.EditableColumnInfo<R, Boolean>() {
        override fun getColumnClass() = Boolean::class.java
        override fun getWidth(table: JTable) = 50
        override fun getName() = message("settings.mappings.${function.name}.title")
        override fun valueOf(item: R): Boolean = function.call(item)
        override fun isCellEditable(item: R) = false
    }
}
