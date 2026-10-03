package org.litvin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The dependency rules of `org.litvin.analytics` (`docs/architecture-rules.md`, B-9):
 * 1) Feature packages use only `Analytics`, `AnalyticsEvent` (with its nested types), and `DisabledAnalytics`. Only
 *    `app` and `ui.privacy` use the rest of the package.
 * 2) `analytics` does not depend on any other `org.litvin` package.
 */
class AnalyticsDependencyTest {
    private val srcRoot: Path = Paths.get("src", "main", "kotlin")
    private val reference = Regex("""org\.litvin\.[A-Za-z0-9_.]*[A-Za-z0-9_]""")

    @Test
    fun `feature packages use only the analytics facade and the events`() {
        val violations = sources()
            .filter { (pkg, _) -> !pkg.startsWith("org.litvin.analytics") && pkg !in FULL_ACCESS }
            .flatMap { (pkg, file) ->
                codeReferences(file)
                    .filter { it.startsWith("org.litvin.analytics.") && !isFacade(it) }
                    .map { "[${srcRoot.relativize(file)}] package $pkg uses $it" }
            }
        assertTrue(
            violations.isEmpty(),
            "Feature packages can use only Analytics, AnalyticsEvent, and DisabledAnalytics:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `analytics depends on no other org_litvin package`() {
        val violations = sources()
            .filter { (pkg, _) -> pkg.startsWith("org.litvin.analytics") }
            .flatMap { (_, file) ->
                codeReferences(file)
                    .filter { !it.startsWith("org.litvin.analytics") }
                    .map { "[${srcRoot.relativize(file)}] uses $it" }
            }
        assertTrue(violations.isEmpty(), "analytics must be a leaf package:\n" + violations.joinToString("\n"))
    }

    private fun isFacade(reference: String): Boolean =
        reference in FACADE || reference.startsWith("org.litvin.analytics.AnalyticsEvent.")

    private fun sources(): List<Pair<String, Path>> = Files.walk(srcRoot).use { stream ->
        stream.asSequence()
            .filter { it.isRegularFile() && it.name.endsWith(".kt") }
            .mapNotNull { file ->
                val pkg = Regex("""^\s*package\s+([a-zA-Z0-9_.]+)""", RegexOption.MULTILINE)
                    .find(Files.readString(file))?.groupValues?.get(1)
                pkg?.let { it to file }
            }
            .toList()
    }

    /** The `org.litvin` names in imports and code. Comment lines are not checked. */
    private fun codeReferences(file: Path): List<String> = Files.readAllLines(file)
        .filter { line -> line.trimStart().let { !it.startsWith("*") && !it.startsWith("/*") && !it.startsWith("//") } }
        .flatMap { line -> reference.findAll(line).map { it.value }.toList() }

    private companion object {
        val FULL_ACCESS = setOf("org.litvin.app", "org.litvin.ui.privacy")
        val FACADE = setOf(
            "org.litvin.analytics.Analytics",
            "org.litvin.analytics.AnalyticsEvent",
            "org.litvin.analytics.DisabledAnalytics",
        )
    }
}
