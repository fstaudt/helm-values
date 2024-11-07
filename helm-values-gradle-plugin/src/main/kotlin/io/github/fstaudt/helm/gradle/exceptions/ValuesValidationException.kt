package io.github.fstaudt.helm.gradle.exceptions

import com.networknt.schema.ValidationMessage
import java.io.File

class ValuesValidationException(valuesFile: File, validationMessages: Set<ValidationMessage>) : RuntimeException(
    """
    Validation of file ${valuesFile.name} failed with following errors:
    ${validationMessages.joinToString("\n    - ", "- ") { "$it\n       (from ${it.evaluationPath})" }}
    """.trimIndent()
)
