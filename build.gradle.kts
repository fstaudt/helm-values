subprojects {
    repositories {
        mavenCentral()
    }
}
repositories {
    mavenCentral()
}
plugins {
    kotlin("jvm") version embeddedKotlinVersion apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}
val sonatypeUsername: String? by project
val sonatypePassword: String? by project
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(sonatypeUsername)
            password.set(sonatypePassword)
        }
    }
}
val gradleWrapperVersion: String by project
tasks {
    wrapper {
        gradleVersion = gradleWrapperVersion
    }
}
