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
- :boom: files patch-*.schema.json must be renamed to *.schema.patch.json and must be stored in same folder as Chart.yaml instead of root directory

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
