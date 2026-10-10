package com.n3sensei.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces layer boundaries by scanning imports in main sources.
 * Gradle runs unit tests with the module directory as the working directory.
 */
class ArchitectureRulesTest {

    private val root = File("src/main/java/com/n3sensei/app")

    private fun kotlinFilesIn(subPackage: String): List<File> =
        File(root, subPackage).walkTopDown().filter { it.extension == "kt" }.toList()

    private fun violations(subPackage: String, forbiddenImports: List<String>): List<String> =
        kotlinFilesIn(subPackage).flatMap { file ->
            file.readLines()
                .filter { line -> line.startsWith("import ") && forbiddenImports.any { line.contains(it) } }
                .map { "${file.relativeTo(root)}: $it" }
        }

    @Test
    fun sourceRootExists() {
        assertTrue("Unexpected working directory: ${File(".").absolutePath}", root.isDirectory)
    }

    @Test
    fun uiLayersDoNotTouchDatabaseOrDataLayer() {
        val forbidden = listOf("androidx.room", "com.n3sensei.app.data.", "com.n3sensei.app.app.di")
        val found = listOf("feature", "core/ui", "core/designsystem").flatMap { violations(it, forbidden) }
        assertTrue("UI must go through ViewModels/domain:\n${found.joinToString("\n")}", found.isEmpty())
    }

    @Test
    fun featuresDoNotDependOnEachOtherOrOnNavigation() {
        val featureDirs = File(root, "feature").listFiles { f -> f.isDirectory }.orEmpty()
        val found = featureDirs.flatMap { dir ->
            val others = featureDirs.filter { it != dir }.map { "com.n3sensei.app.feature.${it.name}." }
            violations("feature/${dir.name}", others + "androidx.navigation" + "com.n3sensei.app.app.")
        }
        assertTrue("Features must stay independent:\n${found.joinToString("\n")}", found.isEmpty())
    }

    @Test
    fun domainIsPureKotlin() {
        val forbidden = listOf("android.", "androidx.", "com.n3sensei.app.data.", "com.n3sensei.app.feature.")
        val found = violations("domain", forbidden)
        assertTrue("Domain must not depend on Android or other layers:\n${found.joinToString("\n")}", found.isEmpty())
    }
}
