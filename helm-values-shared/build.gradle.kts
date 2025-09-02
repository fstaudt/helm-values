plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka") version "2.0.0"
    `maven-publish`
    signing
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.apache.httpcomponents.client5:httpclient5:5.5")
    api("org.apache.commons:commons-compress:1.28.0") {
        api("org.apache.commons:commons-lang3:3.18.0")
    }
    api("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
    api("com.github.java-json-tools:json-patch:1.13")
    testImplementation(projects.helmValuesTest)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    group = "build"
    from(sourceSets.main.get().allSource)
}
val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    group = "documentation"
    from(tasks.getByName("dokkaGeneratePublicationHtml").outputs)
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
