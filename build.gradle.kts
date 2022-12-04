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
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}
val sonatypeUsername: String? by project
val sonatypePassword: String? by project
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
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
