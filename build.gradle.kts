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
val sonatypeUsername = providers.gradleProperty("sonatypeUsername").orNull
val sonatypePassword = providers.gradleProperty("sonatypePassword").orNull
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
val gradleWrapperVersion: String = providers.gradleProperty("gradleWrapperVersion").get()
tasks {
    wrapper {
        gradleVersion = gradleWrapperVersion
    }
}
