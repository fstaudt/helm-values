plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.junit.jupiter:junit-jupiter-api:6.0.0")
    api("org.assertj:assertj-core:3.27.6")
    api("net.javacrumbs.json-unit:json-unit-assertj:4.1.1")
    api("org.wiremock:wiremock:3.13.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}
