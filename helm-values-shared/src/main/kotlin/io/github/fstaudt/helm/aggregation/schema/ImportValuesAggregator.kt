package io.github.fstaudt.helm.aggregation.schema

import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.PROPERTIES
import io.github.fstaudt.helm.JsonSchemaConstants.Keywords.REF
import io.github.fstaudt.helm.ObjectNodeExtensions.allOf
import io.github.fstaudt.helm.ObjectNodeExtensions.objectNode
import io.github.fstaudt.helm.ObjectNodeExtensions.props
import io.github.fstaudt.helm.ObjectNodeExtensions.splitNotBlanks
import io.github.fstaudt.helm.model.ChartDependencyImport

object ImportValuesAggregator {

    fun ObjectNode.addImportValueReferencesFor(importValues: List<ChartDependencyImport>, schemaPath: String) {
        importValues.forEach { importValue ->
            val importPath = importValue.child.splitNotBlanks(".")
                .fold(schemaPath) { basePath, field -> "$basePath/$PROPERTIES/$field" }
            val importNode = importValue.parent.splitNotBlanks(".")
                .fold(this) { node, s -> node.props().objectNode(s) }
            if (importNode.isEmpty) {
                importNode.put(REF, importPath)
            } else {
                importNode.allOf().add(objectNode().put(REF, importPath))
            }
        }
    }
}
