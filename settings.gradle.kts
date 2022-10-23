rootProject.name = "helm-values"
include(
    "helm-values-gradle-plugin",
    "helm-values-intellij-plugin",
    "helm-values-shared",
    "helm-values-test",
)
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
