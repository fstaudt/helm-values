package io.github.fstaudt.helm.gradle

import org.junit.jupiter.api.DisplayNameGenerator
import java.lang.reflect.Method

/**
 * JUnit test name generator that appends the tested Gradle version to all test names.
 */
class GradleVersionDisplayNameGenerator : DisplayNameGenerator.Standard() {
    private val displayNameSuffix = displayNameSuffix()
    override fun generateDisplayNameForMethod(testClass: Class<*>, testMethod: Method): String {
        return "${testMethod.name}$displayNameSuffix"
    }

    private fun displayNameSuffix(): String = System.getProperty("displayNameSuffix")
}
