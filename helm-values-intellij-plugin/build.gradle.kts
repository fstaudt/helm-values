import org.jetbrains.changelog.Changelog.OutputType.HTML
import org.jetbrains.changelog.markdownToHTML

val intellijPluginName: String by project
val intellijPluginVersion="$version"
val intellijPluginSinceBuild: String by project
val intellijPluginUntilBuild: String by project
val intellijPlatformType: String by project
val intellijPlatformVersion: String by project
val intellijPlatformPlugins: String by project

plugins {
    // Java support
    id("java")
    // Kotlin support
    kotlin("jvm")
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.11.0"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.0.0"
}

// Configure project's dependencies
repositories {
    mavenCentral()
}

dependencies {
    api(projects.helmValuesShared) {
        exclude(module = "slf4j-api")
    }
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation(projects.helmValuesTest) {
        exclude(module = "junit-jupiter-api")
        exclude(module = "wiremock-jre8")
    }
}

// Set the JVM language level used to compile sources and generate files - Java 11 is required since 2020.3
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(intellijPluginName)
    version.set(intellijPlatformVersion)
    type.set(intellijPlatformType)

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(intellijPlatformPlugins.split(',').map(String::trim).filter(String::isNotEmpty))
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(intellijPluginVersion)
    groups.set(
        listOf(
            "✨ New",
            "\uD83D\uDC1B Fixed",
            "\uD83D\uDD12 Security",
            "\uD83D\uDDD1 Deprecated",
            "\uD83D\uDD25 Removed"
        )
    )

}

tasks {
    patchPluginXml {
        version.set(intellijPluginVersion)
        sinceBuild.set(intellijPluginSinceBuild)
        untilBuild.set(intellijPluginUntilBuild)

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.renderItem(changelog.run { getOrNull(intellijPluginVersion) ?: getLatest() }, HTML)
        })
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(intellijPluginVersion.split('-').getOrElse(1) { "default" }.split('.').first()))
    }
}
