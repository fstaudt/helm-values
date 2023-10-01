plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `java-library`
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api("org.junit.jupiter:junit-jupiter-api:5.10.0")
    api("org.assertj:assertj-core:3.24.2")
    api("net.javacrumbs.json-unit:json-unit-assertj:2.38.0") {
        api("net.minidev:json-smart:2.5.0")
    }
    api("com.github.tomakehurst:wiremock-jre8:2.35.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testRuntimeOnly("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}
