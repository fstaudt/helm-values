package io.github.fstaudt.helm

object JsonSchemaConstants {
    const val SCHEMA_VERSION = "https://json-schema.org/draft/2020-12/schema"

    const val GENERATOR_LABEL = "helm-values 0.10.1"

    const val NEW_LINE = "\n\\n"

    const val HELM_SCHEMA_FILE = "values.schema.json"
    const val VALUES_SCHEMA_FILE = "values.schema.json"
    const val PATCH_VALUES_SCHEMA_FILE = "values.schema.patch.json"
    const val PATCH_VALUES_SCHEMA_YAML_FILE = "values.schema.patch.yaml"
    const val AGGREGATED_SCHEMA_FILE = "aggregated-values.schema.json"
    const val PATCH_AGGREGATED_SCHEMA_FILE = "aggregated-values.schema.patch.json"
    const val PATCH_AGGREGATED_SCHEMA_YAML_FILE = "aggregated-values.schema.patch.yaml"

    object Keywords {
        const val PROPERTIES = "properties"
        const val GLOBAL = "global"
        const val ALL_OF = "allOf"
        const val REQUIRED = "required"
        const val REF = "\$ref"
        const val ID = "\$id"
        const val SCHEMA = "\$schema"
        const val ADDITIONAL_PROPERTIES = "additionalProperties"
        const val UNEVALUATED_PROPERTIES = "unevaluatedProperties"
    }
}
