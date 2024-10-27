package io.github.fstaudt.helm.idea.settings.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align.Companion.FILL
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.table.TableModelEditor
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.HelmValuesSettings
import io.github.fstaudt.helm.idea.HelmValuesSettings.Companion.HELM_BINARY
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import io.github.fstaudt.helm.idea.settings.service.ChartRepositoryService
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
    companion object {
        internal const val FIELD_REQUIRED = "settings.field.required"
    }

    private val chartRepositoriesEditor = TableModelEditor(
        arrayOf(
            Column("charts", ChartRepository::name, 40),
            Column("charts", ChartRepository::url, 150),
            BooleanColumn("charts", ChartRepository::secured, 50),
            BooleanColumn("charts", ChartRepository::pushedToHelm, 90)
        ),
        ChartRepositoryEditor(),
        message("settings.charts.none")
    ).disableUpDownActions()
    private val chartJsonSchemaRepositoryMappingsViewer = TableModelEditor(
        arrayOf(
            Column("mappings", JsonSchemaRepositoryMapping::name, 40),
            Column("mappings", JsonSchemaRepositoryMapping::baseUri, 150),
            Column("mappings", JsonSchemaRepositoryMapping::valuesSchemaFile, 50),
            Column("mappings", JsonSchemaRepositoryMapping::globalValuesSchemaFile, 60),
            BooleanColumn("mappings", JsonSchemaRepositoryMapping::secured, 50)
        ),
        JsonSchemaRepositoryMappingEditor(),
        message("settings.chartMappings.none")
    ).apply {
        enabled(false)
    }
    private val additionalJsonSchemaRepositoryMappingsEditor = TableModelEditor(
        arrayOf(
            Column("mappings", JsonSchemaRepositoryMapping::name, 40),
            Column("mappings", JsonSchemaRepositoryMapping::baseUri, 150),
            Column("mappings", JsonSchemaRepositoryMapping::valuesSchemaFile, 50),
            Column("mappings", JsonSchemaRepositoryMapping::globalValuesSchemaFile, 60),
            BooleanColumn("mappings", JsonSchemaRepositoryMapping::secured, 50)
        ),
        JsonSchemaRepositoryMappingEditor(),
        message("settings.mappings.none")
    ).disableUpDownActions()
    private lateinit var helmBinaryPath: Cell<JBTextField>

    override fun createPanel(): DialogPanel {
        val state = HelmValuesSettings.instance().state
        return panel {
            rowWithTextFieldForProperty(state::helmBinaryPath) { cell ->
                cell.focused().also { helmBinaryPath = it }
            }
            row {
                cell(chartRepositoriesEditor.createComponent())
                    .align(FILL)
                    .label(message("settings.charts.label"), LabelPosition.TOP)
            }.resizableRow()
            row {
                cell(chartJsonSchemaRepositoryMappingsViewer.createComponent().apply { preferredHeight = 162 })
                    .align(FILL)
                    .label(message("settings.chartMappings.label"), LabelPosition.TOP)
            }
            row {
                cell(additionalJsonSchemaRepositoryMappingsEditor.createComponent().apply { preferredHeight = 162 })
                    .align(FILL)
                    .label(message("settings.mappings.label"), LabelPosition.TOP)
            }
        }
    }

    override fun isModified(): Boolean {
        val state = HelmValuesSettings.instance().state
        val chartRepositoryService = ChartRepositoryService.instance()
        return additionalJsonSchemaRepositoryMappingsEditor.model.items.sortedBy { it.name } != additionalJsonSchemaRepositoryMappings()
                || chartRepositoriesEditor.model.items.sortedBy { it.name } != chartRepositoryService.list()
                || helmBinaryPath.component.text.trimOrElse(HELM_BINARY) != state.helmBinaryPath
    }

    override fun apply() {
        val state = HelmValuesSettings.instance().state
        val chartRepositoryService = ChartRepositoryService.instance()
        val jsonSchemaRepositoryMappingService = JsonSchemaRepositoryMappingService.instance()
        val project = PROJECT.getData(DataManager.getInstance().getDataContext(getPreferredFocusedComponent()))
        jsonSchemaRepositoryMappingService.update(allJsonSchemaRepositoryMappings())
        chartRepositoryService.update(project, chartRepositoriesEditor.model.items)
        state.helmBinaryPath = helmBinaryPath.component.text.trimOrElse(HELM_BINARY)
        reset()
    }

    override fun reset() {
        val state = HelmValuesSettings.instance().state
        val chartRepositoryService = ChartRepositoryService.instance()
        val chartRepositories = chartRepositoryService.list()
        chartRepositoriesEditor.reset(chartRepositories)
        chartJsonSchemaRepositoryMappingsViewer.reset(chartRepositories.mapNotNull { it.toJsonSchemaRepositoryMapping() })
        additionalJsonSchemaRepositoryMappingsEditor.reset(additionalJsonSchemaRepositoryMappings())
        helmBinaryPath.component.text = state.helmBinaryPath
    }

    private fun allJsonSchemaRepositoryMappings(): List<JsonSchemaRepositoryMapping> {
        return (chartRepositoriesEditor.model.items.mapNotNull { it.toJsonSchemaRepositoryMapping() }
                + additionalJsonSchemaRepositoryMappingsEditor.model.items)
            .distinctBy { it.name }
            .filter { it.name.isNotBlank() }
    }

    private fun additionalJsonSchemaRepositoryMappings(): List<JsonSchemaRepositoryMapping> {
        val chartRepositoryService = ChartRepositoryService.instance()
        val jsonSchemaRepositoryMappingService = JsonSchemaRepositoryMappingService.instance()
        val chartRepositories = chartRepositoryService.list()
        return jsonSchemaRepositoryMappingService.list().filter { mapping ->
            chartRepositories.mapNotNull { it.toJsonSchemaRepositoryMapping()?.name }.contains(mapping.name).not()
        }
    }

    private class Column<T, C>(
        private val tableName: String,
        private val field: KMutableProperty1<T, C>,
        private val preferredWidth: Int? = null,
    ) : TableModelEditor.EditableColumnInfo<T, C>() {
        override fun getName() = message("settings.$tableName.${field.name}.title")
        override fun getPreferredStringValue() = preferredWidth?.let { "".padEnd(it) }
        override fun valueOf(item: T): C = field.get(item)
        override fun setValue(item: T, value: C) {
            field.set(item, value)
        }

        override fun isCellEditable(item: T) = false
    }

    private class BooleanColumn<R>(
        private val tableName: String,
        private val function: KFunction<Boolean>,
        private val width: Int,
    ) : TableModelEditor.EditableColumnInfo<R, Boolean>() {
        override fun getColumnClass() = Boolean::class.java
        override fun getWidth(table: JTable) = width
        override fun getName() = message("settings.$tableName.${function.name}.title")
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
