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
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.settings.model.ChartRepository
import io.github.fstaudt.helm.idea.settings.service.ChartRepositoryService
import io.github.fstaudt.helm.idea.settings.ui.HelmValuesConfigurable.Companion.FIELD_REQUIRED
import java.net.URI
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty0

class ChartRepositoryEditor : TableModelEditor.DialogItemEditor<ChartRepository> {
    override fun getItemClass() = ChartRepository::class.java
    override fun applyEdited(oldItem: ChartRepository, newItem: ChartRepository) {
        oldItem.applyFrom(newItem)
    }

    override fun clone(item: ChartRepository, forInPlaceEditing: Boolean): ChartRepository {
        return if (forInPlaceEditing) item else item.copy()
    }

    override fun isUseDialogToAdd() = true
    override fun edit(
        item: ChartRepository,
        mutator: Function<in ChartRepository, out ChartRepository>,
        isAdd: Boolean
    ) {
        val mappings = ChartRepositoryService.instance.list()
        val referenceMappings = mappings.filter { !it.referenced() && it.name != item.name }.map { it.name }
        lateinit var name: Cell<JBTextField>
        lateinit var url: Cell<JBTextField>
        lateinit var referenced: Cell<JBCheckBox>
        lateinit var referenceRepository: Cell<ComboBox<String>>
        lateinit var password: Cell<JBPasswordField>
        lateinit var username: Cell<JBTextField>
        val panel = panel {
            rowWithTextFieldForProperty(item::name) { cell ->
                cell.focused().also { name = it }
            }
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
            rowWithComboBoxForProperty(item::referenceRepository, referenceMappings) { cell ->
                cell.component.addActionListener {
                    username.component.text = ""
                    password.component.text = ""
                    mappings.firstOrNull { it.name == "${cell.component.selectedItem}" }?.let {
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
        textFieldFn: (Cell<JBTextField>) -> Cell<JBTextField> = { it }
    ): Row {
        return row(message("settings.charts.${prop.name}.label")) {
            textField().forProperty(prop, textFieldFn)
        }
    }

    private fun Panel.rowWithPasswordFieldForProperty(
        prop: KMutableProperty0<String>,
        passwordFieldFn: (Cell<JBPasswordField>) -> Cell<JBPasswordField> = { it }
    ): Row {
        return row(message("settings.charts.${prop.name}.label")) {
            cell(JBPasswordField()).forProperty(prop, passwordFieldFn)
        }
    }

    private fun Panel.rowWithComboBoxForProperty(
        prop: KMutableProperty0<String>,
        items: List<String>,
        comboboxFn: (Cell<ComboBox<String>>) -> Cell<ComboBox<String>> = { it }
    ): Row {
        return row(message("settings.charts.${prop.name}.label")) {
            comboBox(listOf("") + items)
                .accessibleName(prop.name)
                .bindItem(prop.toNullableProperty())
                .columns(COLUMNS_LARGE)
                .comment(message("settings.charts.${prop.name}.comment"))
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
            .comment(message("settings.charts.${prop.name}.comment"))
            .let { textFieldFn(it) }
    }
}
