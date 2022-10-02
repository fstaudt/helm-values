# helm values assistant

![Build](https://github.com/fstaudt/helm-values/workflows/Build/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.fstaudt.helm/helm-values-shared)

**IntelliJ**\
[![Version](https://img.shields.io/jetbrains/plugin/v/19970.svg)](https://plugins.jetbrains.com/plugin/19970-helm-values-assistant)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/19970.svg)](https://plugins.jetbrains.com/plugin/19970-helm-values-assistant)

**Gradle**\
![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fstaudt.helm-values)

Generate JSON schemas to help writing values for Helm charts.

The project provides several plugins to generate [JSON schemas](https://json-schema.org/) for a Helm chart.\
These schemas can then be used to document, validate and auto-complete Helm values in your IDE:

- [Gradle plugin](helm-values-gradle-plugin/README.md)
- [IntelliJ plugin](helm-values-intellij-plugin/README.md)

It only supports Helm3 and requires all dependencies to be defined in `Chart.yaml`.\
File `dependencies.yaml` previously used in Helm2 to define dependencies is not supported.

Since Helm3, Helm charts can contain a [JSON schema](https://helm.sh/docs/topics/charts/#schema-files)
named `values.schema.json` to validate values when Helm chart is installed.\
The plugins can extract the JSON schemas from all chart dependencies and aggregate those in a single JSON schema.\
The aggregated JSON schema can then be used to provide auto-completion and documentation on values.yaml in your IDE.

The plugins can also be configured to download JSON schemas from external JSON schemas repositories.\
This can be useful to provide documentation for Helm charts that do not contain a JSON schema.

Finally, the gradle plugin can also be used to generate and publish JSON schemas to external JSON schemas repositories.

All business logic of the plugins is maintained in a [java shared library](helm-values-shared/README.md) published on maven
Central.\
This library could be used to provide JSON schema generation for other IDE / tools (e.g. Maven plugin).

## Configure JSON schemas repositories

As explained in introduction, plugins can be configured to integrate with external JSON schema repositories.

This can be useful to provide documentation for Helm charts that do not contain a JSON schema.\
It can also be useful if you only want JSON schema validation to be informative:\
*Helm install fails when JSON schema is packaged in the chart and JSON schema validation fails.*

Configuration of JSON schemas repositories is documented for each plugin:

- [Gradle plugin](helm-values-gradle-plugin/README.md#configure-json-schemas-repositories)
- [IntelliJ plugin](helm-values-intellij-plugin/README.md#screenshots)

### JSON schema repository structure

Each schema repository should be structured as follows:

``` shell
repository
 |- chart-name
     |- chart-version
         |- values.schema.json             # JSON schema for values.yaml (may include a reference to schema for global section)
         |- global-values.schema.json      # JSON schema for global section in values.yaml
```

For more information on `global-values.schema.json`, check dedicated section
on [separate JSON schema for global values](#separate-json-schema-for-global-values).

### Custom JSON schema file names

Default file names for JSON schemas can be overridden for each repository.

### JSON schema repository security

JSON schemas repositories can be secured with basic authentication.

Each schema repository can be configured with user and password.

## Separate JSON schema for global values

[Global values](https://helm.sh/docs/chart_template_guide/subcharts_and_globals/#global-chart-values) can be defined at
top level in values.yaml even if they are only used in a dependency.

The rules of aggregation of JSON schemas for simple and global values are therefore not the same.\
For this reason, JSON schema for global values has been defined as a separate schema named `global-values.schema.json`.

Given the following Chart.yaml:

```yaml
apiVersion: v2
name: my-bundle
version: 0.1.0
dependencies:
  - name: other-bundle
    version: 0.2.0
    repository: "@bundles"
  - name: simple-app
    version: 0.3.0
    repository: "@apps"
```

Generated JSON schema `values.schema.json` for values would be:

```yaml
properties:
  global:
    $ref: "global-values.schema.json"
  other-bundle:
    $ref: "other-bundle/0.2.0/values.schema.json"
  simple-app:
    $ref: "simple-app/0.3.0/values.schema.json"
```

Generated JSON schema `global-values.schema.json` for global values would be:

```yaml
allOf:
  - $ref: "other-bundle/0.2.0/global-values.schema.json"
  - $ref: "simple-app/0.3.0/global-values.schema.json"
```

## Patch generated JSON schemas

In some cases, generated JSON schema do not contain enough information.\
This can be the case when the chart defines its own templates and its own values.

To answer this need, plugins use [json-patch](https://github.com/java-json-tools/json-patch) library
to patch the generated JSON schemas.

Patch is enabled by creation of a file in the base folder of the chart (same folder as Chart.yaml):

- `values.schema.patch.json`: patch `values.schema.json` generated
  by [Gradle task generateJsonSchemas](helm-values-gradle-plugin/README.md#generatejsonschemas)
- `global-values.schema.patch.json`:
  patch `global-values.schema.json` generated
  by [Gradle task generateJsonSchemas](helm-values-gradle-plugin/README.md#generatejsonschemas)
- `aggregated-values.schema.patch.json`:
  patch `aggregated-values.schema.json` generated
  by [Gradle task aggregateJsonSchema](helm-values-gradle-plugin/README.md#aggregatejsonschema)
  or [aggregation actions in IntelliJ plugin](helm-values-intellij-plugin/README.md#screenshots)
