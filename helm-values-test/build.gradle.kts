import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `java-library`
}

dependencies {
    api("org.junit.jupiter:junit-jupiter-api:5.9.1")
    api("org.assertj:assertj-core:3.23.1")
    api("net.javacrumbs.json-unit:json-unit-assertj:2.36.0") {
        api("net.minidev:json-smart:2.4.8")
    }
    api("com.github.tomakehurst:wiremock-jre8:2.35.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testRuntimeOnly("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

java {
    targetCompatibility = VERSION_1_8
}
