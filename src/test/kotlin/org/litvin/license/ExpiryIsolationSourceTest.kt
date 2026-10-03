package org.litvin.license

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * E7-S1 and E7-S3 (build-expiry-spec.md, "Tests"): only the production setup makes the real parts of the build expiry.
 * The real parts are the fetcher of the rules file, the rules URL, the time store, and the flag store.
 * A test that makes them can read GitHub or write the saved time of the installed app.
 * Comment lines are not checked.
 */
class ExpiryIsolationSourceTest {
    private val mainRoot: Path = Paths.get("src", "main", "kotlin")
    private val testRoot: Path = Paths.get("src", "test", "kotlin")

    private val realParts = listOf(
        "HttpRulesFetcher(",
        "RULES_URL",
        "PreferencesSavedTimeStore(",
        "PreferencesExpiredFlagStore(",
        "productionExpiryController",
    )

    @Test
    fun `in src main only the production setup and the definition files refer to the real parts`() {
        val allowed = setOf(
            "org/litvin/app/AppServices.kt",
            "org/litvin/license/online/RulesFetcher.kt",
            "org/litvin/license/time/SavedTimeStore.kt",
            "org/litvin/license/check/ExpiredFlagStore.kt",
        )
        val violations = codeLines(mainRoot)
            .filter { it.file !in allowed && realParts.any(it.text::contains) }
            .map { it.toString() }

        assertTrue(violations.isEmpty(), "Make the real parts only in AppServices.production():\n" + violations.joinToString("\n"))
    }

    @Test
    fun `no property and no environment variable changes the expiry`() {
        val expiryFiles = listOf("org/litvin/license/check/", "org/litvin/license/online/", "org/litvin/license/time/")
        val read = Regex("""System\.getenv|System\.getProperty\((?!"java\.home"\))""")
        val violations = codeLines(mainRoot)
            .filter { line -> expiryFiles.any(line.file::startsWith) || line.file.matches(Regex("org/litvin/license/[^/]+\\.kt")) }
            .filter { read.containsMatchIn(it.text) }
            .map { it.toString() }

        assertTrue(violations.isEmpty(), "The build expiry must not read a property or an environment variable:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `in src test only the tests on the allow list refer to the real fetcher and to AppServices production`() {
        // These tests use only local servers, or read the source text.
        val allowed = setOf(
            "org/litvin/license/online/HttpRulesFetcherTest.kt",
            "org/litvin/license/online/UpdateTrustTest.kt",
            "org/litvin/NetworkGuardExtensionTest.kt",
            "org/litvin/StartOrderSourceTest.kt",
            "org/litvin/license/ExpiryIsolationSourceTest.kt",
        )
        val forbidden = listOf("HttpRulesFetcher(", "RULES_URL", "productionExpiryController")
        val lines = codeLines(testRoot).filter { it.file !in allowed }
        val violations = lines.filter { line -> forbidden.any(line.text::contains) }.map { it.toString() }
        // AppServices.production() with a factory is permitted when the factory gives a fake expiry.
        val production = lines.filter { "AppServices.production(" in it.text }
            .filter { line -> lines.none { it.file == line.file && "expiry =" in it.text } }
            .map { "$it (no 'expiry =' in the file)" }

        assertTrue(
            (violations + production).isEmpty(),
            "Give the tests a fake fetcher (TestExpiry, RecordingRulesFetcher):\n" + (violations + production).joinToString("\n"),
        )
    }

    @Test
    fun `tests that use the preferences stores of the expiry do not use the real preferences`() {
        val stores = listOf("PreferencesSavedTimeStore(", "PreferencesExpiredFlagStore(", "PreferencesProvider.LICENSE")
        val realPreferences = listOf("Preferences.userRoot", "userNodeForPackage", "PreferencesProvider.production")
        val lines = codeLines(testRoot).filter { it.file != "org/litvin/license/ExpiryIsolationSourceTest.kt" }
        val storeFiles = lines.filter { line -> stores.any(line.text::contains) }.map { it.file }.toSet()
        val violations = lines
            .filter { it.file in storeFiles && realPreferences.any(it.text::contains) }
            .map { it.toString() }

        assertTrue(violations.isEmpty(), "Use InMemoryPreferencesProvider for the expiry stores:\n" + violations.joinToString("\n"))
    }

    private data class CodeLine(val file: String, val number: Int, val text: String) {
        override fun toString() = "[$file:$number] ${text.trim()}"
    }

    private fun codeLines(root: Path): List<CodeLine> = Files.walk(root).use { stream ->
        stream.asSequence()
            .filter { it.isRegularFile() && it.name.endsWith(".kt") }
            .flatMap { file ->
                val relative = root.relativize(file).toString().replace('\\', '/')
                Files.readAllLines(file).withIndex()
                    .filter { (_, line) -> line.trimStart().let { !it.startsWith("*") && !it.startsWith("/*") && !it.startsWith("//") } }
                    .map { (index, line) -> CodeLine(relative, index + 1, line) }
            }
            .toList()
    }
}
