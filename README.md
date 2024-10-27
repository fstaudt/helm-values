# Helm values

![Build](https://github.com/fstaudt/helm-values/workflows/Build/badge.svg?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/io.github.fstaudt.helm/helm-values-shared)

**IntelliJ**\
[![Version](https://img.shields.io/jetbrains/plugin/v/19970.svg)](https://plugins.jetbrains.com/plugin/19970-helm-values-assistant)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/19970.svg)](https://plugins.jetbrains.com/plugin/19970-helm-values-assistant)

**Gradle**\
![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fstaudt.helm-values)

**Ease configuration of values for [Helm charts](https://helm.sh/) in your IDE**

The project provides several plugins to generate [JSON schemas](https://json-schema.org/) for values of Helm charts.\
These schemas can then be used to document, validate and auto-complete Helm values in your IDE:

- [IntelliJ plugin](helm-values-intellij-plugin/README.md)
- [Gradle plugin](helm-values-gradle-plugin/README.md)

The plugins can extract the [JSON schemas](https://helm.sh/docs/topics/charts/#schema-files) named `values.schema.json` from all chart dependencies.\
They can also be configured to download JSON schemas from external JSON schema repositories.

The aggregated JSON schema can then be used to provide auto-completion and documentation on values.yaml in your IDE.

Finally, the Gradle plugin can also be used to generate and publish JSON schemas to external JSON schema repositories.

All business logic of the plugins is maintained in a [java shared library](helm-values-shared/README.md) published on maven Central repository.\
This java library can be used to provide JSON schema generation for other IDE or build tools (e.g. Maven plugin).

### Supported by

<table width="100%">
<tr><td align="center">
<img src="supports/jetbrains.svg" height="150" alt="JetBrains"/>

[Licenses for Open Source Development](https://jb.gg/OpenSourceSupport)

</td>
<td>
<img src="supports/SBS.svg" height="150" alt="SBS"/>

[SBS](https://www.soprabanking.com)

</td>
</tr>
</table>

## JSON schemas

### Generated JSON schemas

The plugins generate several JSON schemas to support the validation of Helm values in different contexts.

#### aggregated-values-schema.json

Schema `aggregated-values.schema.json` is generated
by [Gradle task aggregateJsonSchema](helm-values-gradle-plugin/README.md#aggregatejsonschema)
or [aggregation actions in IntelliJ plugin](helm-values-intellij-plugin/README.md#refresh-jon-schemas-for-current-chart)
.

It is intended to provide documentation, validation and auto-completion in your IDE on values.yaml of the current chart.

It aggregates:

- JSON schemas extracted from packages of chart dependencies
- JSON schemas of dependencies downloaded from external JSON schema repositories

JSON schemas are only extracted from package of a chart dependency if there is no external JSON schema repository
defined for this dependency.\
A fallback schema is generated if package of chart dependency is missing or invalid.\
The description of the fallback schema provides more information on the extract issue.

JSON schemas are only downloaded from external JSON schema repositories if an external JSON schema repository defined
for this dependency.\
A fallback schema is generated if JSON schema can't be downloaded from the external repository (schema not found,
network failure ...).
The description of the fallback schema provides more information on the download issue.

It is also intended to provide documentation, validation and auto-completion on extra values applied on a
packaged chart.\
e.g.:

```shell
helm install my-chart-1.2.3.tgz -f extra-values.yaml
```

#### values.schema.json

Schema `values.schema.json` is generated
by [Gradle task generateJsonSchemas](helm-values-gradle-plugin/README.md#generatejsonschemas).

It is intended to be published in a JSON schema repository, so that it can be referenced in schemas of other charts.

This schema contains several references (`$ref`) to other schemas and shouldn't be packaged in Helm charts.

#### global-values.schema.json (*deprecated*)

Schema `global-values.schema.json` was generated
by [Gradle task generateJsonSchemas](helm-values-gradle-plugin/README.md#generatejsonschemas) until version 0.3.0.

It was intended to be published in a JSON schema repository, so that it can be referenced in schemas of other charts.

*Since [#11 (0.4.0)](https://github.com/fstaudt/helm-values/issues/11), schema `global-values.schema.json` is no more generated and published to external repositories.*\
*It is however still used in generation of schema `aggregated-values.schema.json` for retro-compatibility with schemas
generated with previous versions.*\
*The support of global values schema files in aggregation is however deprecated and will be removed in `1.0.0`.*

### Patch for generated JSON schemas

In some cases, generated JSON schemas may not contain enough information.\
This can be the case when the chart defines its own templates and its own values.

To allow such customizations, plugins use [json-patch](https://github.com/java-json-tools/json-patch) library
to patch the generated JSON schemas.

Patch is enabled by creation of a file in the base folder of the chart (same folder as `Chart.yaml`):

- `values.schema.patch.json` or `values.schema.patch.yaml`: patch for `values.schema.json` generated
  by [Gradle task generateJsonSchemas](helm-values-gradle-plugin/README.md#generatejsonschemas) \
  *Since [#12 (0.4.0)](https://github.com/fstaudt/helm-values/issues/12),
  this patch file also impacts `aggregated-values.schema.json` that is based on `values.schema.json`.*
- `aggregated-values.schema.patch.json` or `aggregated-values.schema.patch.yaml`: additional patch for
  `aggregated-values.schema.json` generated by
  [Gradle task aggregateJsonSchema](helm-values-gradle-plugin/README.md#aggregatejsonschema) or
  by [IntelliJ action to refresh JSON schemas](helm-values-intellij-plugin/README.md#refresh-jon-schemas-for-current-chart)

Patch files in YAML format have been introduced with [#55 (0.9.0)](https://github.com/fstaudt/helm-values/issues/55).*\
If JSON patch file is present, YAML patch file is ignored.

## JSON schema validation

Generated JSON schemas can be used in multiple ways to validate content of `values.yaml` of a Helm chart:

- in **IntelliJ IDEA** using JSON schema validation features provided by IntelliJ IDEA,
- in **Visual Studio code** using JSON schema validation features provided by VS code plugins,
- with **Gradle** using [new Gradle task `validateHelmValues`](helm-values-gradle-plugin/README.md#validatehelmvalues)
  introduced in 0.7.0.

### JSON schema validation results

It is important to be aware that IntelliJ IDEA, VS code & Gradle plugins use different JSON schema validators.\
The same generated JSON schemas may therefore produce different validation results depending on the tool used to
validate it.

As an example, generated JSON schemas
use [keyword `unevaluatedProperties`]((https://json-schema.org/understanding-json-schema/reference/object.html#unevaluated-properties))
introduced in Draft 2019-09.\
IntelliJ IDEA only supports JSON schema specification up to Draft-07 and therefore ignores this keyword.\
Validation errors linked to this keyword are therefore not correctly identified by IntelliJ IDEA.\
On the other hand, Gradle plugin
uses [JSON schema validator from networknt](https://github.com/networknt/json-schema-validator) and fully supports
Draft 2020-12.\
Validation errors linked to keyword `unevaluatedProperties` are therefore be correctly identified by Gradle plugin.

Usage of `unevaluatedProperties` keyword in generated JSON schemas is required to allow correct JSON schema composition,
as explained
in [JSON schema specification](https://json-schema.org/understanding-json-schema/reference/object.html#unevaluated-properties).\
Validation differences should disappear when Draft 2019-09 is supported by JSON schema validators of all IDE.

## JSON schema repositories

Gradle and IntelliJ plugins can be configured to download JSON schemas from external JSON schema repositories.

### Why use external repositories for JSON schemas ?

External schema repositories can be useful to provide documentation for Helm charts that do not contain a JSON schema.

They can also be useful if you only want JSON schema validation to be only informative in your IDE.\
`helm install` fails when provided values are not validated by the packaged JSON schema.

Finally, JSON schemas stored in external JSON schema repositories can contain references (`$ref`) to other schemas.\
This gives more flexibility and industrialization capabilities
compared to JSON schemas packaged in Helm charts that must be self-contained.\
Gradle and IntelliJ plugins ensure that referenced JSON schemas are also downloaded
and that references in the downloaded schemas are updated to use the locally downloaded schemas.

### Configure JSON schema repositories

Repository mappings can be configured in plugins to define JSON schema repository for each Helm repository.

Plugins use the repository key in dependencies of `Chart.yaml` to define the JSON schema repository
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

The plugins can be configured with following configuration to download JSON schemas for the first 2 dependencies:

| Helm repository ID | JSON schema repository                  |
|--------------------|-----------------------------------------|
| `@bundles`         | `https://my-schemas/repository/bundles` |
| `@apps`            | `https://my-schemas/repository/apps`    |

More information on configuration of JSON schema repositories is provided in
[Gradle plugin](helm-values-gradle-plugin/README.md#configure-json-schema-repositories) and
[IntelliJ plugin](helm-values-intellij-plugin/README.md#configure-json-schema-repositories) documentation.

### JSON schema repository structure

Each schema repository should be structured as follows:

``` shell
repository
 |- chart-with-single-schema
     |- chart-version
         |- values.schema.json             # JSON schema for values.yaml (including global section)
 |- chart-with-separate-global-schema
     |- chart-version
         |- values.schema.json             # JSON schema for values.yaml (including a reference to schema for global section)
         |- global-values.schema.json      # JSON schema for global section in values.yaml
```

### Custom JSON schema file names

File names for JSON schemas can be overridden for each repository.\
Default values are provided:

- JSON schema for values.yaml: `values.schema.json`
- JSON schema for global section in values.yaml (*deprecated*): `global-values.schema.json`

More information to configure custom file names is provided in
[Gradle plugin](helm-values-gradle-plugin/README.md#custom-json-schema-file-names) and
[IntelliJ plugin](helm-values-intellij-plugin/README.md#custom-json-schema-file-names) documentation.

### JSON schema repository security

JSON schema repositories can be secured with basic authentication.

Each schema repository can be configured with user and password.

More information to configure security for repositories is provided in
[Gradle plugin](helm-values-gradle-plugin/README.md#json-schema-repository-security) and
[IntelliJ plugin](helm-values-intellij-plugin/README.md#json-schema-repository-security) documentation.

