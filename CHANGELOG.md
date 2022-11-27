## 0.4.1 - 2022-11-27

- :sparkles: add generation info in generated schemas
- :sparkles: (intellij) add actions for all charts in popup menu
- :bug: download should update downloaded schemas when dependency version is
  updated ([#31](https://github.com/fstaudt/helm-values/issues/31))

## 0.4.0 - 2022-11-16

- :sparkles: (intellij) actions to clear JSON schemas for current chart or all charts
- :sparkles: merge global-values.schema.json in global properties of
  values.schema.json ([#11](https://github.com/fstaudt/helm-values/issues/11))
- :sparkles: extract should not extract JSON schemas from dependency when repository is in repository mappings
- :sparkles: reuse patch of values.schema.json for
  aggregated-values.schema.json ([#12](https://github.com/fstaudt/helm-values/issues/12))
- :sparkles: aggregate global values of extracted JSON schemas in aggregated JSON
  schema ([#13](https://github.com/fstaudt/helm-values/issues/13))
- :sparkles: improve readability of error messages in fallback
  schemas ([#14](https://github.com/fstaudt/helm-values/issues/14))
- :sparkles: title and description for aggregation of global values
- :sparkles: schema for validation of extra values applied on packaged
  charts ([#16](https://github.com/fstaudt/helm-values/issues/16))
- :sparkles: plugins renamed to "Helm values"
- :zap: download should keep schemas already downloaded
- :zap: (gradle) cache output of task downloadJsonSchemas
- :bug: (intellij) HelmValuesJsonSchemaProvider should be disabled when virtual file parent is
  null ([#8](https://github.com/fstaudt/helm-values/issues/8))
- :bug: extractor should extract charts & sub-charts JSON schemas from dependency archives
- :bug: Chart.yaml should be correctly parsed when dependency contain no version or
  repository ([#28](https://github.com/fstaudt/helm-values/issues/28))

**BREAKING CHANGES**:

- :boom: Since [#11](https://github.com/fstaudt/helm-values/issues/11),
  patch file `global-values.schema.patch.json` is no more used and should be removed.\
  Patches previously configured in this file must be reintegrated in patch file `values.schema.patch.json`.

## 0.3.0 - 2022-09-16

- :sparkles: rename patch-*schema.json files to *.schema.patch.json
- :construction_worker: rename module helm-values-commons to helm-values-shared
- :zap: move internal mappers to companion objects
- :bug: downloader should ignore invalid $ref and download next $ref
- :bug: downloader should download JSON schemas in same folder when $ref is relative in same folder
- :construction_worker: bump dependency versions for jackson modules
- :construction_worker: bump gradle from 7.5 to 7.5.1

**BREAKING CHANGES**:

- :boom: imports of helm-values-commons must be renamed to helm-values-shared
- :boom: files patch-*.schema.json must be renamed to *.schema.patch.json and must be stored in same folder as
  Chart.yaml instead of root directory

## 0.2.0 - 2022-08-10

- :sparkles: extract business logic in shared library

## 0.1.3 - 2022-06-27

- :bug: generated global JSON schema should be empty when chart has no dependencies

## 0.1.2 - 2022-06-26

- :bug: tasks should get Chart.yaml in provided sources directory when it is specified

## 0.1.1 - 2022-06-20

- :bug: aggregateJsonSchema should treat aggregated patch file as input when it exists
- :bug: generateJsonSchemas should treat patch files as inputs when they exist

## 0.1.0 - 2022-06-20

- :sparkles: aggregate JSON schemas of Helm chart dependencies for assistance on Helm values in your IDE
- :sparkles: download & publication to external JSON schemas repositories
- :sparkles: patch generated JSON schemas
