## 0.9.0 - Jun 14, 2024

- :sparkles: (intellij) configure Helm chart repositories from IntelliJ IDEA
  ([#137](https://github.com/fstaudt/helm-values/issues/137))
- :sparkles: (intellij) configure Chart and JSON schema repositories in same editor in settings
  ([#137](https://github.com/fstaudt/helm-values/issues/137))
- :sparkles: support of YAML format for patch files
  ([#55](https://github.com/fstaudt/helm-values/issues/55))
- :sparkles: support import-values in chart dependencies
  ([#106](https://github.com/fstaudt/helm-values/issues/106))
- :sparkles: (intellij) support for IJ 2024.2
- :sparkles: (intellij) drop support for IJ 2022.3
- :bug: fix deprecation error on ActionUpdateThread since 2024.1
- :bug: fix extraction of JSON schema from dependency archive when dependency is provided with version range

**BREAKING CHANGES**:

- :boom: (intellij) IntelliJ IDEA 2022.3 is no more supported.\
  Extension is only available for IntelliJ IDEA 2023.1 and above.
- :boom: (gradle) since [#55](https://github.com/fstaudt/helm-values/issues/55),
  property `patchValuesFile` must be renamed `valuesPatchFile` for Gradle tasks aggregateJsonSchema and
  generateJsonSchemas
- :boom: (gradle) since [#55](https://github.com/fstaudt/helm-values/issues/55),
  property `patchAggregatedFile` must be renamed `aggregatedValuesPatchFile` for Gradle tasks aggregateJsonSchema

## 0.8.2 - Mar 23, 2024

- :sparkles: (intellij) support IJ 2024.1

## 0.8.1 - Feb 20, 2024

- :sparkles: (gradle) compatibility with Gradle configuration cache
- :bug: (gradle) disable caching by default for task validateHelmValues
- :bug: (intellij) use alphabetical order for actions on all charts in project

**BREAKING CHANGES**:

- Minimal Gradle version for Gradle plugin: Gradle 8.3

## 0.8.0 - Nov 11, 2023

- :sparkles: discard required properties when already defined in dependency values
  ([#40](https://github.com/fstaudt/helm-values/issues/40))
- :sparkles: (intellij) actions to trigger helm dependency update
  ([#53](https://github.com/fstaudt/helm-values/issues/53))
- :sparkles: (intellij) actions in error notifications to install or configure Helm
- :sparkles: (intellij) support IJ 2023.3
- :bug: avoid NullPointerException in JSON schema providers when virtual file parent is null
  ([#114](https://github.com/fstaudt/helm-values/issues/114))
- :bug: discard generation of extra-values.schema.json
  ([#130](https://github.com/fstaudt/helm-values/issues/130))
- :bug: disable additionalProperties at top level of aggregated chart
  ([#131](https://github.com/fstaudt/helm-values/issues/131))
- :bug: (intellij) UnsupportedOperationException when values.yaml is part of a virtual file system
  ([#132](https://github.com/fstaudt/helm-values/issues/132))
- :bug: JSON schema extractor should take aliases into account
  ([#133](https://github.com/fstaudt/helm-values/issues/133))

**BREAKING CHANGES**:

- Since [#130](https://github.com/fstaudt/helm-values/issues/130),
  JSON schema validation is no more applied by default on files `*-values.yaml` in IntelliJ IDEA.\
  Validation of non-standard Helm values files with generated JSON schema can be configured
  in [IntelliJ settings](https://www.jetbrains.com/help/idea/json.html#ws_json_schema_add_custom).

## 0.7.1 - Jul 14, 2023

- :sparkles: (intellij) support for IntelliJ IDEA 2023.2
- :zap: (intellij) avoid full project scanning in JSON schema provider factory
  ([#64](https://github.com/fstaudt/helm-values/issues/64))
- :bug: invalid $ref in aggregated JSON schemas should be removed
  ([#103](https://github.com/fstaudt/helm-values/issues/103))
- :bug: (intellij) refresh JSON schemas of dependent charts before current chart
  ([#104](https://github.com/fstaudt/helm-values/issues/104))

## 0.7.0 - May 18, 2023

- :sparkles: (gradle) task to validate values.yaml against aggregated JSON schema
  ([#18](https://github.com/fstaudt/helm-values/issues/18))
- :sparkles: aggregate downloaded & extracted JSON schemas in a single file
  ([#49](https://github.com/fstaudt/helm-values/issues/49))
- :sparkles: aggregate chart & inter-dependent charts JSON schemas in a single file
  ([#49](https://github.com/fstaudt/helm-values/issues/49))
- :sparkles: remove additional and unevaluated properties from aggregated sub-charts
  ([#49](https://github.com/fstaudt/helm-values/issues/49))
- :bug: JSON schema aggregation should use unevaluatedProperties in global properties
  ([#89](https://github.com/fstaudt/helm-values/issues/89))
- :bug: full URI for $id of aggregated JSON schema
  ([#90](https://github.com/fstaudt/helm-values/issues/90))
- :bug: $ref should not be used with other properties in generated JSON schemas
  ([#91](https://github.com/fstaudt/helm-values/issues/91))
- :bug: use of version ranges for locally stored dependencies causes failure in JSON schemas aggregation
  ([#93](https://github.com/fstaudt/helm-values/issues/93))

**BREAKING CHANGES**:

- :boom: Since [#89](https://github.com/fstaudt/helm-values/issues/89) &
  [#91](https://github.com/fstaudt/helm-values/issues/91)
  and due to the lack of support for Draft 2019-09 in IntelliJ IDEA,
  some invalid values are no more identified by IntelliJ IDEA.

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
