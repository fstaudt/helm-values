# Changelog

## 0.10.1 - Mar 24, 2025

### ✨ New

- drop support for global values schema
- support IntelliJ IDEA 2025.1
- drop support for IntelliJ IDEA 2023.3
- rename IntelliJ plugin name to "Helm values"

### 🐛 Fixed

- avoid usage of internal IntelliJ IDEA classes

### 🔥 Removed

- IntelliJ IDEA 2023.3 is no more supported.\
  Extension is only available for IntelliJ IDEA 2024.1 and above.
- global values schema (deprecated since 0.4.0) is no more supported.

## 0.10.0 - Nov 3, 2024

### ✨ New

- update Helm repositories in separate IntelliJ action
  ([#165](https://github.com/fstaudt/helm-values/issues/165))
- warning in settings when passwords are not saved
  ([#156](https://github.com/fstaudt/helm-values/issues/156))
- support for IJ 2024.3
- explicit dependency on json module
  ([#162](https://github.com/fstaudt/helm-values/issues/162))

### 🐛 Fixed
- use on-demand instances of services instead of class instances
  ([#161](https://github.com/fstaudt/helm-values/issues/161))
- rename .idea/json-schemas to .idea/helm-values for clarity
- search for helm charts in project should ignore internal Gradle folder

## 0.9.0 - Jun 14, 2024

### ✨ New

- configure Helm chart repositories from IntelliJ IDEA settings
  ([#137](https://github.com/fstaudt/helm-values/issues/137))
- configure Chart and JSON schema repository in same editor in settings
  ([#137](https://github.com/fstaudt/helm-values/issues/137))
- support YAML format for patch files
  ([#55](https://github.com/fstaudt/helm-values/issues/55))
- support import-values in chart dependencies
  ([#106](https://github.com/fstaudt/helm-values/issues/106))
- support for IJ 2024.2
- drop support for IJ 2022.3

### 🐛 Fixed

- fix deprecation error on ActionUpdateThread since 2024.1
- fix extraction of JSON schema from dependency archive when dependency is provided with version range

### 🔥 Removed
 
- IntelliJ IDEA 2022.3 is no more supported.\
  Extension is only available for IntelliJ IDEA 2023.1 and above.

## 0.8.2 - Mar 23, 2024

### ✨ New
- support for IntelliJ IDEA 2024.1

## 0.8.1 - Feb 20, 2024

### 🐛 Fixed
- use alphabetical order for actions on all charts in project

## 0.8.0 - Nov 11, 2023

### ✨ New
- discard required properties when already defined in dependency values
  ([#40](https://github.com/fstaudt/helm-values/issues/40))
- actions to trigger helm dependency update
  ([#53](https://github.com/fstaudt/helm-values/issues/53))
- actions in error notifications to install or configure Helm
- support for IntelliJ IDEA 2023.3

### 🐛 Fixed
- avoid NullPointerException in JSON schema providers when virtual file parent is null
  ([#114](https://github.com/fstaudt/helm-values/issues/114))
- discard generation of extra-values.schema.json
  ([#130](https://github.com/fstaudt/helm-values/issues/130))
- disable additionalProperties at top level of aggregated chart
  ([#131](https://github.com/fstaudt/helm-values/issues/131))
- UnsupportedOperation exception when values.yaml is part of a virtual file system
  ([#132](https://github.com/fstaudt/helm-values/issues/132))
- JSON schema extractor should take aliases into account
  ([#133](https://github.com/fstaudt/helm-values/issues/133))

### 🔥 Removed
- Since [#130](https://github.com/fstaudt/helm-values/issues/130),
  JSON schema validation is no more applied by default on files `*-values.yaml` in IntelliJ IDEA.\
  Validation of non-standard Helm values files with generated JSON schema can be configured
  in [IntelliJ settings](https://www.jetbrains.com/help/idea/json.html#ws_json_schema_add_custom).

## 0.7.1 - Jul 14, 2023

### ✨ New
- support for IntelliJ IDEA 2023.2

### 🐛 Fixed
- invalid $ref in aggregated JSON schemas should be removed
  ([#103](https://github.com/fstaudt/helm-values/issues/103))
- refresh JSON schemas of dependent charts before current chart
  ([#104](https://github.com/fstaudt/helm-values/issues/104))
- avoid full project scanning in JSON schema provider factory
  ([#64](https://github.com/fstaudt/helm-values/issues/64))

## 0.7.0 - May 18, 2023

### ✨ New
- aggregate downloaded & extracted JSON schemas in a single file
  ([#49](https://github.com/fstaudt/helm-values/issues/49))
- aggregate chart & inter-dependent charts JSON schemas in a single file
  ([#49](https://github.com/fstaudt/helm-values/issues/49))
- remove additional and unevaluated properties from aggregated sub-charts
  ([#49](https://github.com/fstaudt/helm-values/issues/49))

### 🐛 Fixed
- JSON schema aggregation should use unevaluatedProperties in global properties
  ([#89](https://github.com/fstaudt/helm-values/issues/89))
- full URI for $id of aggregated JSON schema
  ([#90](https://github.com/fstaudt/helm-values/issues/90))
- $ref should not be used with other properties in generated JSON schemas
  ([#91](https://github.com/fstaudt/helm-values/issues/91))
- use of version ranges for locally stored dependencies causes failure in JSON schemas aggregation
  ([#93](https://github.com/fstaudt/helm-values/issues/93))

### 🔥 Removed

- Since [#89](https://github.com/fstaudt/helm-values/issues/89) &
  [#91](https://github.com/fstaudt/helm-values/issues/91)
  and due to the lack of support for Draft 2019-09 in IntelliJ IDEA,
  some invalid values are no more identified by IntelliJ IDEA.

## 0.6.1 - Mar 26, 2023

### ✨ New
- support for IntelliJ IDEA 2023.1
- drop support for IntelliJ IDEA 2021.3
- improve search for VirtualFile in JsonSchema providers
- restrict actions for current chart to main Helm files

### 🔥 Removed

- IntelliJ IDEA 2021.3 is no more supported.\
  Extension is only available for IntelliJ IDEA 2022.1 and above.
- Actions for current chart are no more available from any file in chart directory.\
  They are only available from chart directory itself, `values.yaml` or `Chart.yaml`.

## 0.6.0 - Jan 29, 2023

### ✨ New

- reuse credentials from other JSON schema repository mapping
  ([#32](https://github.com/fstaudt/helm-values/issues/32))
- include values.schema.json of chart folder in aggregated schema
  ([#41](https://github.com/fstaudt/helm-values/issues/41))
- add x-generated fields in fallback schemas
  ([#47](https://github.com/fstaudt/helm-values/issues/47))
- preferred width for columns in settings

### 🐛 Fixed

- support condition with comma separated property names
  ([#50](https://github.com/fstaudt/helm-values/issues/50))
- aggregate should include aggregated JSON schema of dependency when local path ends with /
  ([#52](https://github.com/fstaudt/helm-values/issues/52))

## 0.5.1 - Dec 29, 2022

### 🐛 Fixed

- rename packaged-values.schema.json to extra-values.schema.json
  ([#43](https://github.com/fstaudt/helm-values/issues/43))

### 🔥 Removed

- Since [#43](https://github.com/fstaudt/helm-values/issues/43),
  patch file `packaged-values.schema.patch.json` must be renamed `extra-values.schema.patch.json`.

## 0.5.0 - Dec 23, 2022

### ✨ New

- support for inter-dependent charts stored in the same repository
  ([#30](https://github.com/fstaudt/helm-values/issues/30))
- run processing of intellij actions in background
  ([#17](https://github.com/fstaudt/helm-values/issues/17))
- notifications for successful or failed background processing
  ([#17](https://github.com/fstaudt/helm-values/issues/17))

## 0.4.2 - Dec 4, 2022

### ✨ New

- support for IntelliJ IDEA 2022.3

## 0.4.1 - Nov 27, 2022

### ✨ New

- add generation info in generated schemas
- add actions for all charts in popup menu

### 🐛 Fixed

- download should update downloaded schemas when dependency version is updated
  ([#31](https://github.com/fstaudt/helm-values/issues/31))

## 0.4.0 - Nov 16, 2022

### ✨ New

- actions to clear JSON schemas for current chart or all charts
- merge global-values.schema.json in global properties of values.schema.json
  ([#11](https://github.com/fstaudt/helm-values/issues/11))
- extract should not extract JSON schemas from dependency when repository is in repository mappings
- reuse patch of values.schema.json for aggregated-values.schema.json
  ([#12](https://github.com/fstaudt/helm-values/issues/12))
- aggregate global values of extracted JSON schemas in aggregated JSON schema
  ([#13](https://github.com/fstaudt/helm-values/issues/13))
- improve readability of error messages in fallback schemas
  ([#14](https://github.com/fstaudt/helm-values/issues/14))
- title and description for aggregation of global values
- schema for validation of extra values applied on packaged charts
  ([#16](https://github.com/fstaudt/helm-values/issues/16))
- plugin renamed to "Helm values"
- download should keep schemas already downloaded

### 🐛 Fixed

- HelmValuesJsonSchemaProvider should be disabled when virtual file parent is null
  ([#8](https://github.com/fstaudt/helm-values/issues/8))
- extractor should extract charts & sub-charts JSON schemas from dependency archives
- Chart.yaml should be correctly parsed when dependency contain no version or repository
  ([#28](https://github.com/fstaudt/helm-values/issues/28))

### 🗑 Deprecated

- Since [#11](https://github.com/fstaudt/helm-values/issues/11),
  schema `global-values.schema.json` is no more generated and published to external repositories.*\
  It is however still used in generation of schema `aggregated-values.schema.json` for retro-compatibility with schemas
  generated with previous versions.\
  The support of global values schema files in aggregation is however deprecated and will be removed in `1.0.0`.

### 🔥 Removed

- Since [#11](https://github.com/fstaudt/helm-values/issues/11),
  patch file `global-values.schema.patch.json` is no more used and should be removed.\
  Patches previously stored in this file must be reintegrated in patch file `values.schema.patch.json`.

## 0.1.0 - Sep 19, 2022

### ✨ New

- **Configure JSON schema repository mappings**\
  JSON schema repository mappings configured once for all projects\
  mappings stored in PersistentStateComponent\
  username and passwords stored securely in PasswordSafe
- **Aggregate JSON schema from chart dependencies**\
  Aggregation service\
  Aggregation actions for single chart and whole project
- **JSON schema provider for Helm values.yaml**\
  use aggregated JSON schema for values.yaml
