package io.github.fstaudt.helm.idea.settings.ui

import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.intellij.util.Function
import com.intellij.util.ui.table.TableModelEditor
import io.github.fstaudt.helm.idea.HelmValuesBundle.message
import io.github.fstaudt.helm.idea.settings.model.JsonSchemaRepositoryMapping
import java.net.URI
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0

@Suppress("UnstableApiUsage")
class JsonSchemaRepositoryMappingEditor : TableModelEditor.DialogItemEditor<JsonSchemaRepositoryMapping> {
    override fun getItemClass() = JsonSchemaRepositoryMapping::class.java
    override fun applyEdited(oldItem: JsonSchemaRepositoryMapping, newItem: JsonSchemaRepositoryMapping) =
        oldItem.applyFrom(newItem)

    override fun clone(item: JsonSchemaRepositoryMapping, forInPlaceEditing: Boolean) =
        if (forInPlaceEditing) item else item.copy()

    override fun isUseDialogToAdd() = true
    override fun edit(
        item: JsonSchemaRepositoryMapping,
        mutator: Function<in JsonSchemaRepositoryMapping, out JsonSchemaRepositoryMapping>,
        isAdd: Boolean
    ) {
        val panel = panel {
            rowWithTextField(item::name) { it.focused() }
            rowWithTextField(item::baseUri) { baseUri ->
                baseUri.validationOnInput {
                    error("Invalid base URI").takeIf {
                        runCatching { !URI(baseUri.component.text).isAbsolute }.getOrElse { true }
                    }
                }
            }
            rowWithTextField(item::username)
            row(message("settings.mappings.password.label")) {
                cell(JBPasswordField())
                    .bindText(item::password)
                    .columns(COLUMNS_LARGE)
                    .comment(message("settings.mappings.password.comment"))
            }
            rowWithTextField(item::valuesSchemaFile)
            rowWithTextField(item::globalValuesSchemaFile)
        }
        dialog(title = message("settings.mappings.dialog.title"), panel = panel, ok = {

            return@dialog null
        }).show()
        mutator.`fun`(item)
    }

    private fun Panel.rowWithTextField(
        prop: KMutableProperty0<String>,
        textFieldFn: (Cell<JTextComponent>) -> Unit = {}
    ): Row {
        return row(message("settings.mappings.${prop.name}.label")) {
            textField()
                .accessibleName(prop.name)
                .bindText(prop)
                .columns(COLUMNS_LARGE)
                .comment(message("settings.mappings.${prop.name}.comment"))
                .also { textFieldFn(it) }
        }
    }
}
