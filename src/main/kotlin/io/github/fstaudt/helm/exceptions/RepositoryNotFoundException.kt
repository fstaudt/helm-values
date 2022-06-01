package io.github.fstaudt.helm.exceptions

class RepositoryNotFoundException(publicationRepository: String?)
    : RuntimeException("""
        publication repository $publicationRepository not found in repository mappings.
        Please correct configuration of helmValuesAssistant in gradle build.
    """.trimIndent())
