import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `maven-publish`
}

dependencies {
    api("org.apache.httpcomponents.client5:httpclient5:5.1.3")
    api("org.apache.commons:commons-compress:1.21")
    api("com.fasterxml.jackson.core:jackson-databind:2.13.4")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.4")
    api("com.github.java-json-tools:json-patch:1.13")
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

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        register<MavenPublication>("mavenJava").configure {
            from(components["java"])
            artifact(sourcesJar)
            groupId = "${project.group}"
            artifactId = project.name
        }
    }
}
