import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.1.0"
}

val pluginVersion = "$version"
val pluginName = "helm-values"
gradlePlugin {
    plugins {
        register(pluginName) {
            id = "io.github.fstaudt.$pluginName"
            displayName = "Helm values"
            description = "Generate JSON schemas to help writing values for Helm charts!"
            implementationClass = "$group.gradle.HelmValuesPlugin"
            version = pluginVersion
        }
    }
}

pluginBundle {
    website = "https://github.com/fstaudt/helm-values"
    vcsUrl = "https://github.com/fstaudt/helm-values"
    tags = listOf("helm", "chart", "kubernetes", "json", "schema")
}

dependencies {
    compileOnly(gradleKotlinDsl())
    api(kotlin("gradle-plugin"))
    api(projects.helmValuesShared)
    testImplementation(gradleTestKit())
    testImplementation(projects.helmValuesTest)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

java {
    targetCompatibility = VERSION_1_8
}
