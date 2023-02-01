import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka") version "1.7.20"
    `maven-publish`
    signing
}

dependencies {
    api("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    api("org.apache.commons:commons-compress:1.22")
    api("com.fasterxml.jackson.core:jackson-databind:2.14.1")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
    api("com.github.java-json-tools:json-patch:1.13")
    testImplementation(projects.helmValuesTest)
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
val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.getByName("dokkaHtml").outputs)
}

publishing {
    publications {
        register<MavenPublication>("mavenJava").configure {
            pom {
                name.set("${project.group}.${project.name}")
                description.set("Utility classes to generate JSON schema for values of Helm charts")
                url.set("https://github.com/fstaudt/helm-values")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("François Staudt")
                        email.set("fstaudt@gmail.com")
                        url.set("https://github.com/fstaudt")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/fstaudt/helm-values.git")
                    developerConnection.set("scm:git:ssh://github.com:fstaudt/helm-values.git")
                    url.set("https://github.com/fstaudt/helm-values/tree/main")
                }
            }
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            groupId = "${project.group}"
            artifactId = project.name
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}
