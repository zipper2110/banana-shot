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
 * Guardrails: dependency hygiene for UI packages.
 *
 * Rules enforced:
 * 1) No imports from one tab package into another:
 *    org.litvin.ui.tabs.<A> must not import org.litvin.ui.tabs.<B> when A != B
 * 2) ui.commons must not import any ui.tabs.* packages.
 *
 * How to run locally: mvn -q -Dtest=ArchitectureDependencyHygieneTest test
 */
class ArchitectureDependencyHygieneTest {

    private val srcRoot: Path = Paths.get("src", "main", "kotlin")

    @Test
    fun dependencyHygiene() {
        val violations = mutableListOf<String>()

        if (!Files.exists(srcRoot)) {
            // If sources are absent, nothing to check
            assertTrue(true)
            return
        }

        Files.walk(srcRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .forEach { file ->
                    val text = String(Files.readAllBytes(file))
                    val pkg = extractPackage(text) ?: return@forEach
                    val imports = extractImports(text)

                    // Rule 2: ui.commons must not import ui.tabs.*
                    if (pkg.startsWith("org.litvin.ui.commons")) {
                        imports.filter { it.startsWith("org.litvin.ui.tabs.") }
                            .forEach { imp ->
                                violations += formatViolation(
                                    file,
                                    pkg,
                                    imp,
                                    "ui.commons must not depend on ui.tabs.*"
                                )
                            }
                    }

                    // Rule 1: tab -> other tab forbidden
                    val tabPrefix = "org.litvin.ui.tabs."
                    if (pkg.startsWith(tabPrefix)) {
                        val currentTab = pkg.removePrefix(tabPrefix).substringBefore('.')
                        if (currentTab.isNotBlank()) {
                            imports.filter { it.startsWith(tabPrefix) }
                                .forEach { imp ->
                                    val importedTab = imp.removePrefix(tabPrefix).substringBefore('.')
                                    if (importedTab.isNotBlank() && importedTab != currentTab) {
                                        violations += formatViolation(
                                            file,
                                            pkg,
                                            imp,
                                            "Tab-to-tab import is forbidden"
                                        )
                                    }
                                }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found ${violations.size} architecture dependency violation(s):")
                violations.forEachIndexed { idx, v ->
                    appendLine("${idx + 1}. $v")
                }
                appendLine()
                appendLine("Rules:")
                appendLine(" - No imports from one tab package into another (org.litvin.ui.tabs.<A> -> org.litvin.ui.tabs.<B>)")
                appendLine(" - ui.commons must not import any org.litvin.ui.tabs.*")
            }
            assertTrue(false, message)
        }
    }

    /**
     * All UI colors come from ui.commons.Palette, so that a theme can change them in one place.
     * A color value in a different UI file (a hex or RGB literal, a named AWT color, a "#RRGGBB" string,
     * or a darker() or brighter() variant) is a violation. Comment lines are not checked.
     */
    @Test
    fun uiColorsComeFromPalette() {
        val uiRoots = listOf(srcRoot.resolve("org/litvin/ui"), srcRoot.resolve("org/litvin/app"))
        val palette = srcRoot.resolve("org/litvin/ui/commons/Palette.kt")
        val colorValue = Regex(
            """Color\(\s*(0x|\d)|Color\.(WHITE|BLACK|RED|GREEN|BLUE|GRAY|LIGHT_GRAY|DARK_GRAY|YELLOW|ORANGE|CYAN|MAGENTA|PINK|""" +
                """white|black|red|green|blue|gray|lightGray|darkGray|yellow|orange|cyan|magenta|pink)\b|""" +
                """"[^"]*#[0-9A-Fa-f]{6}|\.(darker|brighter)\(\)""",
        )
        val violations = uiRoots.filter { Files.exists(it) }.flatMap { root ->
            Files.walk(root).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.name.endsWith(".kt") && it != palette }
                    .flatMap { file ->
                        Files.readAllLines(file).withIndex()
                            .filter { (_, line) -> line.trimStart().let { !it.startsWith("*") && !it.startsWith("/*") && !it.startsWith("//") } }
                            .filter { (_, line) -> colorValue.containsMatchIn(line) }
                            .map { (index, line) -> "[${srcRoot.relativize(file)}:${index + 1}] ${line.trim()}" }
                    }
                    .toList()
            }
        }
        assertTrue(
            violations.isEmpty(),
            "Found ${violations.size} color value(s) outside Palette. Use a Palette token, or add one:\n" +
                violations.joinToString("\n"),
        )
    }

    private fun extractPackage(text: String): String? {
        val regex = Regex("^\\s*package\\s+([a-zA-Z0-9_.]+)", RegexOption.MULTILINE)
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun extractImports(text: String): List<String> {
        val regex = Regex("^\\s*import\\s+([a-zA-Z0-9_.*]+)", RegexOption.MULTILINE)
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun formatViolation(file: Path, pkg: String, imp: String, cause: String): String {
        val rel = try { srcRoot.relativize(file).toString() } catch (e: Exception) { file.toString() }
        return "[$rel] package $pkg imports $imp — $cause"
    }
}
