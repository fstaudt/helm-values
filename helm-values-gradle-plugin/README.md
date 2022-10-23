# Gradle plugin for Helm values
![Build](https://github.com/fstaudt/helm-values/workflows/Build/badge.svg)
![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fstaudt.helm-values)

The plugin provides several tasks to generate [JSON schemas](https://json-schema.org/) for a Helm chart.\
These schemas can then be used to document, validate and auto-complete Helm values in your IDE.

Since Helm3, Helm charts can contain a [JSON schema](https://helm.sh/docs/topics/charts/#schema-files)
named `values.schema.json` to validate values when Helm chart is installed.\
The plugin can extract the JSON schemas from all chart dependencies and aggregate those in a single JSON schema.\
The aggregated JSON schema can then be used to provide auto-completion and documentation on values.yaml in your IDE.

The plugin can also be configured to download JSON schemas from external JSON schemas repositories.\
This can be useful to provide documentation for Helm charts that do not contain a JSON schema.

Finally, the plugin can also be used to generate and publish JSON schemas to external JSON schemas repositories.

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
    // Key to JSON schemas repository in repositoryMappings for JSON schemas publication
    // Mandatory for tasks generateJsonSchemas & publishJsonSchemas
    publicationRepository = "@apps"
    // Version for JSON schemas publication (overwrites version in Chart.yaml)
    publishedVersion = "0.1.0"
}
```

For more information on `repositoryMappings`, check dedicated section
to [Configure JSON schemas repositories](#configure-json-schemas-repositories).

## Tasks

### extractJsonSchemas

Extract JSON schemas `values.schema.json` from chart dependencies (including sub-charts in dependencies).

Task requires package of all dependencies available in `charts` directory.\
It can only be executed after `helm dependency update` has been successfully executed.

If dependency is not available in `charts` directory or if extraction fails, task generates a fallback empty schema with the
error in schema description.

This task is a dependency of task [aggregateJsonSchema](#aggregatejsonschema).

### downloadJsonSchemas

Download JSON schemas of dependencies from JSON schema repositories.

Task only attempts to download file `values.schema.json` for each dependency
if a repository mapping is defined for the Helm repository of the dependency.\
Check dedicated section to [Configure JSON schemas repositories](#configure-json-schemas-repositories).

If download fails, task generates a fallback empty schema with the error in schema description.

For more information on `global-values.schema.json`, check dedicated section
on [separate JSON schema for global values](#separate-json-schema-for-global-values).

This task is a dependency of task [aggregateJsonSchema](#aggregatejsonschema).

### aggregateJsonSchema

Aggregate extracted and downloaded JSON schemas for assistance on Helm values in your IDE.

Downloaded JSON schema `values.schema.json` has precedence on extracted JSON schema `values.schema.json`.

Optional file `aggregated-values.schema.patch.json` can be created in the base folder of the chart (same folder as Chart.yaml)
to [patch aggregated JSON schema](https://jsonpatch.com/).

For more information on IDE configuration, check dedicated section on [Configure your IDE](#configure-your-ide).

### generateJsonSchemas

Generate JSON schema `values.schema.json` for publication to a repository of JSON
schemas.

Optional file can be created in the base folder of the chart (same folder as Chart.yaml)
to [patch generated JSON schema](https://jsonpatch.com/):

- `values.schema.patch.json`: patch `values.schema.json`

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
of [JsonSchemaPublisher](src/main/kotlin/io/github/fstaudt/helm/http/JsonSchemaPublisher.kt).

## Configure JSON schemas repositories

As explained in introduction, plugin can be configured to integrate with external JSON schema repositories.

This can be useful to provide documentation for Helm charts that do not contain a JSON schema.\
It can also be useful if you only want JSON schema validation to be informative:\
*Helm install fails when JSON schema is packaged in the chart and JSON schema validation fails.*

### JSON schemas repository mappings

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

If JSON schemas are not found in the repository, a fallback empty JSON schema is created instead.

### Custom JSON schema file names

Default file names for JSON schemas can be overridden for each repository in build.gradle.kts.

```kotlin
helmValues {
    repositoryMappings = mapOf(
        "@apps" to JsonSchemaRepository("https://my-schemas/repository", valuesSchemaFile="helm-values.json", globalValuesSchemaFile="helm-global.json")
    )
}
```

**Deprecation notice**: *`globalValuesSchemaFile` is deprecated and will be removed in `1.0.0`.*

### JSON schema repository security

JSON schemas repositories can be secured with basic authentication.

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
