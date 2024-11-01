plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.junit.jupiter:junit-jupiter-api:5.11.3")
    api("org.assertj:assertj-core:3.26.3")
    api("net.javacrumbs.json-unit:json-unit-assertj:3.5.0")
    api("org.wiremock:wiremock:3.9.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}
