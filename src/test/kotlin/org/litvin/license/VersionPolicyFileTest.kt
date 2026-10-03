package org.litvin.license

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The repository test of `release/version-policy.json` ("Release steps" in `build-expiry-spec.md`).
 * A mistake in the file must not stop all users.
 */
class VersionPolicyFileTest {
    private val policyFile = File("release/version-policy.json")

    @Test
    fun `release version-policy json has no problems`() {
        assertTrue(policyFile.isFile, "${policyFile.absolutePath} does not exist")
        val problems = repositoryProblems(policyFile.readBytes(), BuildInfo.VERSION)
        assertEquals(emptyList(), problems, problems.joinToString("\n"))
    }

    @Test
    fun `the check fails for a file with schema 2`() {
        assertFails("""{"schema": 2, "latest": $LATEST, "rules": []}""")
    }

    @Test
    fun `the check fails for a rule with toVersion equal to the version in pom xml`() {
        assertFails("""{"schema": 1, "latest": $LATEST, "rules": [${rule("r", "\"${BuildInfo.VERSION}\"")}]}""")
    }

    @Test
    fun `the check fails for a file that is not valid`() {
        assertFails("""{"schema": 1, "latest": $LATEST}""")
    }

    @Test
    fun `the check fails for a rule that is not valid`() {
        assertFails("""{"schema": 1, "latest": $LATEST, "rules": [{"id": "r", "toVersion": "0.1.0"}]}""")
    }

    @Test
    fun `the check fails for a file with no valid latest`() {
        assertFails("""{"schema": 1, "rules": []}""")
        assertFails("""{"schema": 1, "latest": {"version": "1.4.0"}, "rules": []}""")
    }

    @Test
    fun `the check fails for a version text that does not start with a number`() {
        assertFails("""{"schema": 1, "latest": {"version": "v1.4.0", "downloadUrl": "https://example.invalid"}, "rules": []}""")
        assertFails("""{"schema": 1, "latest": $LATEST, "rules": [${rule("r", "\"v0.1.0\"")}]}""")
        assertFails("""{"schema": 1, "latest": $LATEST, "rules": [${rule("r", "\"0.1.0\"", from = "\"v0.0.1\"")}]}""")
    }

    @Test
    fun `the check fails for a rule that matches latest_version`() {
        assertFails("""{"schema": 1, "latest": $LATEST, "rules": [${rule("r", "\"99.0.0\"", from = "\"90.0.0\"")}]}""")
    }

    @Test
    fun `the check fails for two rules with the same id`() {
        assertFails("""{"schema": 1, "latest": $LATEST, "rules": [${rule("r", "\"0.1.0\"")}, ${rule("r", "\"0.2.0\"")}]}""")
    }

    @Test
    fun `the check passes for a correct rule`() {
        val body = """{"schema": 1, "latest": $LATEST, "rules": [${rule("r", "\"0.1.0\"", from = "\"0.0.1\"")}]}"""
        assertEquals(emptyList(), repositoryProblems(body.toByteArray(), "1.0.0-SNAPSHOT"))
    }

    private fun assertFails(body: String) {
        assertTrue(repositoryProblems(body.toByteArray(), BuildInfo.VERSION).isNotEmpty(), "No problem found in $body")
    }

    private fun rule(id: String, toVersion: String, from: String? = null): String {
        val fromField = if (from != null) """"fromVersion": $from, """ else ""
        return """{"id": "$id", $fromField"toVersion": $toVersion, "stopsOn": "2027-01-15"}"""
    }

    private companion object {
        const val LATEST = """{"version": "95.0.0", "downloadUrl": "https://example.invalid/releases"}"""

        fun repositoryProblems(body: ByteArray, pomVersion: String): List<String> {
            val rules = when (val parse = VersionRulesParser.parse(body)) {
                is VersionRulesParse.NotValid -> return listOf("The file is not valid: ${parse.reason}")
                is VersionRulesParse.UnknownSchema -> return listOf("schema is ${parse.schema}, not ${VersionRulesParser.SCHEMA}")
                is VersionRulesParse.Valid -> if (parse.problems.isNotEmpty()) return parse.problems else parse.rules
            }
            val problems = mutableListOf<String>()
            val latest = rules.latest
            if (latest == null) problems += "The file has no valid latest"
            val versionTexts = listOfNotNull(latest?.version) + rules.rules.flatMap { listOfNotNull(it.fromVersion, it.toVersion) }
            versionTexts.filterNot(Version::startsWithNumber).forEach { problems += "Version text '$it' does not start with a number" }
            for (rule in rules.rules) {
                if (latest != null && rule.matches(Version.parse(latest.version))) {
                    problems += "Rule '${rule.id}' matches latest.version ${latest.version}"
                }
                if (rule.matches(Version.parse(pomVersion))) {
                    problems += "Rule '${rule.id}' matches the version in pom.xml ($pomVersion)"
                }
            }
            rules.rules.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "Two or more rules have the id '$it'" }
            return problems
        }
    }
}
