# Changelog

## [Unreleased]
### ✨ New

### 🐛 Fixed

### 🔒 Security

### 🗑 Deprecated

### 🔥 Removed

## [0.1.0]
### ✨ New
- **configure JSON schema repository mappings**
  - JSON schema repository mappings configured once for all projects
  - mappings stored in PersistentStateComponent
  - username and passwords stored securely in PasswordSafe
- **aggregate JSON schema from chart dependencies**
  - Aggregation service
  - Aggregation actions for single chart and whole project
- **JSON schema provider for Helm values.yaml**
  - use aggregated JSON schema for values.yaml
