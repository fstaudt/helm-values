package io.github.fstaudt.helm.gradle.tasks

import io.github.fstaudt.helm.JsonSchemaConstants.AGGREGATED_SCHEMA_FILE
import io.github.fstaudt.helm.gradle.HelmValuesPlugin.Companion.HELM_VALUES
import io.github.fstaudt.helm.gradle.exceptions.ValuesValidationException
import io.github.fstaudt.helm.gradle.services.JsonMapper
import io.github.fstaudt.helm.gradle.services.YamlMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "no output")
abstract class ValidateHelmValues : DefaultTask() {
    companion object {
        const val VALIDATE_HELM_VALUES = "validateHelmValues"
    }

    @get:InputFile
    @get:PathSensitive(RELATIVE)
    val aggregatedSchemaFile: Provider<RegularFile> = layout.buildDirectory.file("$HELM_VALUES/$AGGREGATED_SCHEMA_FILE")

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    abstract val valuesFile: Property<File>

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Internal
    abstract val yamlMapper: Property<YamlMapper>

    @get:Internal
    abstract val jsonMapper: Property<JsonMapper>

    @TaskAction
    fun validate() {
        val schema = jsonMapper.get().schemaFrom(aggregatedSchemaFile)
        val values = yamlMapper.get().valuesFrom(valuesFile)
        val validationMessages = schema.validate(values)
        if (validationMessages.isNotEmpty()) {
            throw ValuesValidationException(valuesFile.get(), validationMessages)
        }
    }
}
