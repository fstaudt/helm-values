plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
}

kotlin {
    jvmToolchain(11)
}

val pluginVersion = "$version"
val pluginName = "helm-values"
gradlePlugin {
    website.set("https://github.com/fstaudt/helm-values")
    vcsUrl.set("https://github.com/fstaudt/helm-values")
    plugins {
        register(pluginName) {
            id = "io.github.fstaudt.$pluginName"
            displayName = "Helm values"
            description = "Generate JSON schemas to help writing values for Helm charts!"
            implementationClass = "$group.gradle.HelmValuesPlugin"
            version = pluginVersion
            tags.set(listOf("helm", "chart", "kubernetes", "json", "schema"))
        }
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    api(kotlin("gradle-plugin"))
    api(projects.helmValuesShared)
    api("com.networknt", "json-schema-validator", "1.0.81")
    testImplementation(gradleTestKit())
    testImplementation(projects.helmValuesTest)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}
