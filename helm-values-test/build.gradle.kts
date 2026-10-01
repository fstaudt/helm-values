plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.junit.jupiter:junit-jupiter-api:6.1.3")
    api("org.assertj:assertj-core:3.27.7")
    api("net.javacrumbs.json-unit:json-unit-assertj:6.2.0")
    api("org.wiremock:wiremock:3.13.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}
