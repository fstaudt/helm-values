# Changelog

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
