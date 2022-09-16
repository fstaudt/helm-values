import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.0.0"
}

val pluginVersion = "$version"
val pluginName = "helm-values"
gradlePlugin {
    plugins {
        register(pluginName) {
            id = "io.github.fstaudt.$pluginName"
            displayName = "Helm values assistant"
            description = "Generate JSON schemas to validate values of complex Helm charts with Gradle!"
            implementationClass = "$group.gradle.HelmValuesPlugin"
            version = pluginVersion
        }
    }
}

pluginBundle {
    website = "https://github.com/fstaudt/helm-values"
    vcsUrl = "https://github.com/fstaudt/helm-values"
    tags = listOf("helm", "chart", "kubernetes", "json", "schema")
}

dependencies {
    compileOnly(gradleKotlinDsl())
    api(kotlin("gradle-plugin"))
    api(project(":helm-values-shared"))
    testImplementation(gradleTestKit())
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.34.0")
    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.35.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

java {
    targetCompatibility = VERSION_1_8
}
