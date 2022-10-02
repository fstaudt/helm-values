subprojects {
    repositories {
        mavenCentral()
    }
}
repositories {
    mavenCentral()
}
plugins {
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.jetbrains.qodana") version "0.1.13"
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
// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
}
val gradleWrapperVersion: String by project
tasks {
    wrapper {
        gradleVersion = gradleWrapperVersion
    }
}
