import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.17.0"
    `maven-publish`
}

repositories {
    mavenCentral()
}
val pluginVersion = "$version"
val pluginName = "helm-values-assistant"
gradlePlugin {
    plugins {
        register(pluginName) {
            id = "io.github.fstaudt.$pluginName"
            implementationClass = "$group.HelmValuesAssistantPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/fstaudt/helm-values-assistant-gradle-plugin"
    vcsUrl = "https://github.com/fstaudt/helm-values-assistant-gradle-plugin"
    description = "Generate JSON schema for complex Helm charts with Gradle!"
    (plugins) {
        pluginName {
            displayName = "Helm values assistant"
            tags = listOf("helm")
            version = pluginVersion
        }
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    api(kotlin("gradle-plugin"))
    api("org.apache.httpcomponents.client5:httpclient5:5.1.3")
    api("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.3")
    testImplementation(gradleTestKit())
    testImplementation("org.assertj:assertj-core:3.22.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.33.2")
}

tasks.test {
    useJUnitPlatform()
}

java {
    targetCompatibility = VERSION_1_8
}