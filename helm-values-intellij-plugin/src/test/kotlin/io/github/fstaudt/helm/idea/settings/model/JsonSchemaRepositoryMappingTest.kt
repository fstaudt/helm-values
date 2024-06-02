package io.github.fstaudt.helm.idea.settings.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JsonSchemaRepositoryMappingTest {

    companion object {
        private const val APPS = "@apps"
        private const val APPS_URL = "https://nexus/apps"
        private const val USERNAME = "user"
        private const val PASSWORD = "passwd"
        private const val REF = "ref"
        private const val VALUES_SCHEMA = "values.schema.json"
        private const val GLOBAL_SCHEMA = "global.schema.json"
    }

    @Test
    fun `applyFrom should override all values of the object`() {
        val mapping = JsonSchemaRepositoryMapping(APPS, APPS_URL, REF, USERNAME, PASSWORD, VALUES_SCHEMA, GLOBAL_SCHEMA)
        val receiver = JsonSchemaRepositoryMapping()
        val applied = receiver.applyFrom(mapping)
        assert(applied === receiver) { "applyFrom should return the same object" }
        assertThat(applied).usingRecursiveComparison().isEqualTo(mapping)
    }
}
