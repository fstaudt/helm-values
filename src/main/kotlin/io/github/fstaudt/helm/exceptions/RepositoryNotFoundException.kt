package io.github.fstaudt.helm.exceptions

class RepositoryNotFoundException(targetRepository: String?)
    : RuntimeException("""
        targetRepository $targetRepository not found in repository mappings.
        Please correct configuration of helmValuesAssistant in gradle build.
    """.trimIndent())
