package io.github.fstaudt.helm.idea.settings.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ChartRepositoryTest {

    companion object {
        private const val APPS = "apps"
        private const val APPS_URL = "https://nexus/apps"
        private const val USERNAME = "user"
        private const val PASSWORD = "passwd"
        private const val REF = "ref"
        private const val VALUES_SCHEMA = "values.schema.json"
        private const val GLOBAL_SCHEMA = "global.schema.json"
    }

    @Test
    fun `deepCopy should create a deep copy of the object`() {
        val chartRepository = ChartRepository(APPS, APPS_URL, REF, USERNAME, PASSWORD, true,
            JsonSchemaRepositoryMapping("@$APPS", APPS_URL, REF, USERNAME, PASSWORD, VALUES_SCHEMA, GLOBAL_SCHEMA))
        val deepCopy = chartRepository.deepCopy()
        assertThat(deepCopy).isEqualTo(chartRepository)
        assert(deepCopy !== chartRepository) { "Deep copy should return a new object" }
        assert(deepCopy.jsonSchemaRepositoryMapping !== chartRepository.jsonSchemaRepositoryMapping) {
            "JSON schema repository mappings should be the same object."
        }
    }

    @Test
    fun `applyFrom should override all values of the object`() {
        val chartRepository = ChartRepository(APPS, APPS_URL, REF, USERNAME, PASSWORD, true,
            JsonSchemaRepositoryMapping("@$APPS", APPS_URL, REF, USERNAME, PASSWORD, VALUES_SCHEMA, GLOBAL_SCHEMA))
        val receiver = ChartRepository()
        val applied = receiver.applyFrom(chartRepository)
        assert(applied === receiver) { "applyFrom should return the same object" }
        assertThat(applied).usingRecursiveComparison().isEqualTo(chartRepository)
    }
}
