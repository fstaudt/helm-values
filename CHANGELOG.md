## 0.6.1 - Mar 26, 2023

- :sparkles: (intellij) support for IntelliJ IDEA 2023.1
- :sparkles: (intellij) drop support for IntelliJ IDEA 2021.3
- :zap: (intellij) improve search for VirtualFile in JsonSchema providers
- :zap: (intellij) restrict actions for current chart to main Helm files
- :construction_worker: bump Gradle to 8.0.2
- :construction_worker: (gradle) bump JVM toolchain to 11

**BREAKING CHANGES**:

- :boom: IntelliJ IDEA 2021.3 is no more supported.\
  Extension is only available for IntelliJ IDEA 2022.1 and above.
- :boom: Actions for current chart are no more available from any file in chart directory.\
  They are only available from chart directory itself, `values.yaml` or `Chart.yaml`.

## 0.6.0 - Jan 29, 2023

- :sparkles: (intellij) reuse credentials from other JSON schema repository mapping
  ([#32](https://github.com/fstaudt/helm-values/issues/32))
- :sparkles: include values.schema.json of chart folder in aggregated schema
  ([#41](https://github.com/fstaudt/helm-values/issues/41))
- :sparkles: add x-generated fields in fallback schemas
  ([#47](https://github.com/fstaudt/helm-values/issues/47))
- :sparkles: (intellij) preferred width for columns in settings
- :bug: support condition with comma separated property names
  ([#50](https://github.com/fstaudt/helm-values/issues/50))
- :bug: aggregate should include aggregated JSON schema of dependency when local path ends with /
  ([#52](https://github.com/fstaudt/helm-values/issues/52))

## 0.5.1 - Dec 29, 2022

- :bug: (gradle) reference to schema of inter-dependent chart should use published version over chart version
  ([#42](https://github.com/fstaudt/helm-values/issues/42))
- :bug: rename packaged-values.schema.json to extra-values.schema.json
  ([#43](https://github.com/fstaudt/helm-values/issues/43))

**BREAKING CHANGES**:

- :boom: Since [#43](https://github.com/fstaudt/helm-values/issues/43),
  patch file `packaged-values.schema.patch.json` must be renamed `extra-values.schema.patch.json`.\
  Optional property `patchPackagedFile` of task `aggregateJsonSchema` must be renamed `patchExtraValuesFile`.

## 0.5.0 - Dec 23, 2022

- :sparkles: support for inter-dependent charts stored in the same repository
  ([#30](https://github.com/fstaudt/helm-values/issues/30))
- :sparkles: (intellij) run processing of intellij actions in background
  ([#17](https://github.com/fstaudt/helm-values/issues/17))
- :sparkles: (intellij) notifications of processing for intellij actions
  ([#17](https://github.com/fstaudt/helm-values/issues/17))

## 0.4.2 - Dec 4, 2022

- :sparkles: (intellij) support for IntelliJ IDEA 2022.3

## 0.4.1 - Nov 27, 2022

- :sparkles: add generation info in generated schemas
- :sparkles: (intellij) add actions for all charts in popup menu
- :bug: download should update downloaded schemas when dependency version is updated
  ([#31](https://github.com/fstaudt/helm-values/issues/31))

## 0.4.0 - Nov 16, 2022

- :sparkles: (intellij) actions to clear JSON schemas for current chart or all charts
- :sparkles: merge global-values.schema.json in global properties of values.schema.json
  ([#11](https://github.com/fstaudt/helm-values/issues/11))
- :sparkles: extract should not extract JSON schemas from dependency when repository is in repository mappings
- :sparkles: reuse patch of values.schema.json for aggregated-values.schema.json
  ([#12](https://github.com/fstaudt/helm-values/issues/12))
- :sparkles: aggregate global values of extracted JSON schemas in aggregated JSON schema
  ([#13](https://github.com/fstaudt/helm-values/issues/13))
- :sparkles: improve readability of error messages in fallback schemas
  ([#14](https://github.com/fstaudt/helm-values/issues/14))
- :sparkles: title and description for aggregation of global values
- :sparkles: schema for validation of extra values applied on packaged charts
  ([#16](https://github.com/fstaudt/helm-values/issues/16))
- :sparkles: plugins renamed to "Helm values"
- :zap: download should keep schemas already downloaded
- :zap: (gradle) cache output of task downloadJsonSchemas
- :bug: (intellij) HelmValuesJsonSchemaProvider should be disabled when virtual file parent is null
  ([#8](https://github.com/fstaudt/helm-values/issues/8))
- :bug: extractor should extract charts & sub-charts JSON schemas from dependency archives
- :bug: Chart.yaml should be correctly parsed when dependency contain no version or repository
  ([#28](https://github.com/fstaudt/helm-values/issues/28))

**BREAKING CHANGES**:

- :boom: Since [#11](https://github.com/fstaudt/helm-values/issues/11),
  patch file `global-values.schema.patch.json` is no more used and should be removed.\
  Patches previously configured in this file must be reintegrated in patch file `values.schema.patch.json`.

## 0.3.0 - Sep 16, 2022

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

## 0.2.0 - Aug 10, 2022

- :sparkles: extract business logic in shared library

## 0.1.3 - Jun 27, 2022

- :bug: generated global JSON schema should be empty when chart has no dependencies

## 0.1.2 - Jun 26, 2022

- :bug: tasks should get Chart.yaml in provided sources directory when it is specified

## 0.1.1 - Jun 20, 2022

- :bug: aggregateJsonSchema should treat aggregated patch file as input when it exists
- :bug: generateJsonSchemas should treat patch files as inputs when they exist

## 0.1.0 - Jun 20, 2022

- :sparkles: aggregate JSON schemas of Helm chart dependencies for assistance on Helm values in your IDE
- :sparkles: download & publication to external JSON schemas repositories
- :sparkles: patch generated JSON schemas
