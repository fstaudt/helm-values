# Gradle plugin for Helm values

![Build](https://github.com/fstaudt/helm-values/workflows/Build/badge.svg)
![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fstaudt.helm-values)

**Generate JSON schemas to help writing values for [Helm charts](https://helm.sh/).**

The plugin provides several tasks to generate [JSON schemas](https://json-schema.org/) for a Helm chart.\
These schemas can then be used to document, validate and auto-complete Helm values in your IDE.

Since Helm v3, Helm charts can contain a [JSON schema](https://helm.sh/docs/topics/charts/#schema-files)
named `values.schema.json` to validate values when Helm chart is installed.\
The plugin can extract the JSON schemas from all chart dependencies and aggregate those in a single JSON schema.\
The aggregated JSON schema can then be used to provide auto-completion and documentation on values.yaml in your IDE.

The plugin can also be configured to download JSON schemas from external JSON schema repositories.\
This can be useful to provide documentation for Helm charts that do not contain a JSON schema.\
JSON schemas stored in external JSON schema repositories may also contain references (`$ref`) to other schemas.\
Plugin ensure that referenced JSON schemas are downloaded with the main schema and that references are updated to use
the locally downloaded schemas.

Finally, the plugin can also be used to generate and publish JSON schemas to external JSON schema repositories.

*The plugin only supports Helm3: Helm dependencies are only retrieved in `Chart.yaml`.*\
*File `dependencies.yaml` previously used in Helm2 to define dependencies is not supported*.

## Extension configuration

```kotlin
helmValues {
    // Base directory for sources of Helm chart, containing at least Chart.yaml.
    // Default to project base directory.
    sourcesDir = "."
    // Mappings between Helm repository and repository hosting JSON schemas for charts
    // Keys relate to repository in dependencies of Helm chart.
    repositoryMappings = mapOf(
        "@apps" to JsonSchemaRepository("https://my-schemas/repository/json-schemas/apps")
    )
    // Key to JSON schema repository in repositoryMappings for JSON schemas publication
    // Mandatory for tasks generateJsonSchemas & publishJsonSchemas
    publicationRepository = "@apps"
    // Optional version for JSON schemas publication (overwrites version in Chart.yaml)
    publishedVersion = "0.1.0"
}
```

For more information on `repositoryMappings`, check dedicated section
to [Configure JSON schema repositories](#configure-json-schema-repositories).

## Tasks

### extractHelmDependencies

Extract files from chart dependencies (including sub-charts of dependencies):
- JSON schemas `values.schema.json`
- Chart metadata `chart.yaml`
- Chart values `values.yaml`

Task only attempts to extract file `values.schema.json` from archive of each dependency
if a repository mapping is **not** defined for the Helm repository of the dependency.\
Check dedicated section to [configure JSON schema repositories](#configure-json-schema-repositories).

Task requires package of dependency available in `charts` directory.\
It should only be executed after `helm dependency update` has been successfully executed.

If dependency is not available in `charts` directory or if extraction fails,
task generates a fallback empty schema with the error in schema description.

This task is a dependency of task [aggregateJsonSchema](#aggregatejsonschema).

### downloadJsonSchemas

Download JSON schemas of dependencies from JSON schema repositories.

Task only attempts to download file `values.schema.json` for each dependency
if a repository mapping is defined for the Helm repository of the dependency.\
Check dedicated section to [configure JSON schema repositories](#configure-json-schema-repositories).

If download fails, task generates a fallback empty schema with the error in schema description.

This task is a dependency of task [aggregateJsonSchema](#aggregatejsonschema).

### aggregateJsonSchema

Generate JSON schema `aggregated-values.schema.json` by aggregation of extracted and downloaded JSON schemas.\
The schema is intended to provide auto-completion, documentation and validation in your IDE on values.yaml of the
current chart.\
It can also be used to validate values.yaml with Gradle task `validateHelmValues`.

Optional file `aggregated-values.schema.patch.json` 
can be created in the base folder of the chart (same folder as Chart.yaml)
to [patch aggregated JSON schema](https://jsonpatch.com/).\
Since [#55 (0.9.0)](https://github.com/fstaudt/helm-values/issues/55),
optional file `aggregated-values.schema.patch.yaml` can be created in the base folder of the chart.
to [patch aggregated JSON schema](https://jsonpatch.com/).\
If JSON patch file is present, YAML patch file is ignored.

For more information on patch files, check dedicated section
on [patch for generated JSON schemas](../README.md#patch-for-generated-json-schemas).

### validateHelmValues

Validate content of `values.yaml` with JSON schema `aggregated-values.schema.json` generated by `aggregateJsonSchema`.

[JSON schema validator from networknt](https://github.com/networknt/json-schema-validator) is used internally.

### generateJsonSchemas

Generate JSON schema `values.schema.json` for publication to a repository of JSON
schemas.

Optional file `values.schema.patch.json` can be created in the base folder of the chart (same folder as Chart.yaml)
to [patch generated JSON schema](https://jsonpatch.com/).
Since [#55 (0.9.0)](https://github.com/fstaudt/helm-values/issues/55),
optional file `values.schema.patch.yaml` can be created in the base folder of the chart.
to [patch aggregated JSON schema](https://jsonpatch.com/).\
If JSON patch file is present, YAML patch file is ignored.

For more information on patch files, check dedicated section
on [patch for generated JSON schemas](../README.md#patch-for-generated-json-schemas).

Property `publicationRepository` in plugin extension is mandatory and must be an existing key in repositoryMappings.\
Property `publishedVersion` can be defined in plugin extension to overwrite version defined in Chart.yaml.

This task is a dependency of task [publishJsonSchemas](#publishjsonschemas).

### publishJsonSchemas

Publish generated JSON schema `values.schema.json` to a repository of JSON schemas.

Property `publicationRepository` in plugin extension is mandatory and must be an existing key in repositoryMappings.\
Property `publishedVersion` can be defined in plugin extension to overwrite version defined in Chart.yaml.

JSON schemas publication is only supported on Nexus raw repositories.\
Property `jsonSchemaPublisher` of task `publishJsonSchemas` can however be overwritten
to provide a custom implementation
of [io.github.fstaudt.helm.http.JsonSchemaPublisher](../helm-values-shared/src/main/kotlin/io/github/fstaudt/helm/http/JsonSchemaPublisher.kt)
.

## Configure JSON schema repositories

As explained in [introduction](../README.md#json-schema-repositories),
plugin can be configured to download JSON schemas from external JSON schema repositories.

### JSON schema repository mappings

`repositoryMappings` can be configured in plugin extension to define JSON schema repository for each Helm repository.

Plugin uses the repository key in dependencies of `Chart.yaml` to define the JSON schema repository
that must be used to download JSON schemas for each dependency.

Given the following Chart.yaml:

```yaml
apiVersion: v2
name: my-bundle
version: 0.1.0
dependencies:
  - name: another-bundle
    version: 0.2.0
    repository: "@bundles"
  - name: simple-app
    version: 0.3.0
    repository: "@apps"
  - name: thirdparty-chart
    version: 0.4.0
    repository: "@thirdparty"
```

The plugin must be configured with following configuration to download JSON schemas for the first 2 dependencies:

```kotlin
helmValues {
    repositoryMappings = mapOf(
        "@bundles" to JsonSchemaRepository("https://my-schemas/repository/bundles"),
        "@apps" to JsonSchemaRepository("https://my-schemas/repository/apps")
    )
}
```

### Custom JSON schema file names

Default file names for JSON schemas can be overridden for each repository in build.gradle.kts.

```kotlin
helmValues {
    repositoryMappings = mapOf(
        "@apps" to JsonSchemaRepository("https://my-schemas/repository", valuesSchemaFile="helm-values.json")
    )
}
```

### JSON schema repository security

JSON schema repositories can be secured with basic authentication.

Each schema repository can be configured with user and password in plugin extension.\
It is however advised to configure user and password in ~/.gradle/gradle.properties and not directly in
build.gradle.kts.

```kotlin
val repositoryUser: String by project
val repositoryPassword: String by project
helmValues {
    repositoryMappings = mapOf(
        "@bundles" to JsonSchemaRepository("https://my-schemas/repository", repositoryUser, repositoryPassword),
        "@apps" to JsonSchemaRepository("https://my-schemas/repository", repositoryUser, repositoryPassword)
    )
}
```
