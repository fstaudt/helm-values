# Helm values shared library
![Build](https://github.com/fstaudt/helm-values/workflows/Build/badge.svg?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/io.github.fstaudt.helm/helm-values-shared)

Shared library used to maintain business logic for Gradle and IntelliJ plugins.

### How-to

#### Configure GPG key for signing plugin

- Generate new key pair
  ```bash
  gpg --gen-key
  ```
- Distribute public key
  ```bash
  gpg --list-secret-keys --keyid-format long
  gpg --keyserver keyserver.ubuntu.com --send-keys <LONG_KEY_ID>
  gpg --keyserver keys.openpgp.org --send-keys <LONG_KEY_ID>
  ```
- store long key ID in secret `GPG_KEY_NAME`
- store passphrase in secret `GPG_PASSPHRASE`
- Export private key and store it in secret `GPG_PRIVATE_KEY`
  ```bash
  gpg --armor --export-secret-keys <LONG_KEY_ID> | pbcopy
  ```

