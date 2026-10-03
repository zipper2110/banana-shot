package org.litvin.license

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionRulesParserTest {
    private fun parse(json: String) = VersionRulesParser.parse(json.toByteArray())

    private fun valid(json: String) = assertIs<VersionRulesParse.Valid>(parse(json))

    private val latest = """{"version": "1.4.0", "downloadUrl": "https://example.invalid/releases"}"""

    @Test
    fun `the example file of the spec is valid`() {
        val result = valid(
            """
            {
              "schema": 1,
              "latest": {
                "version": "1.4.0",
                "downloadUrl": "https://example.invalid/releases/latest",
                "installerUrl": "https://example.invalid/Setup.exe",
                "notes": "Faster export."
              },
              "rules": [
                {
                  "id": "2027-01-project-save",
                  "fromVersion": "1.2.0",
                  "toVersion": "1.2.1",
                  "stopsOn": "2027-01-15",
                  "message": "Update to 1.2.2 or later."
                }
              ]
            }
            """,
        )
        assertEquals(
            LatestRelease("1.4.0", "https://example.invalid/releases/latest", "https://example.invalid/Setup.exe", "Faster export."),
            result.rules.latest,
        )
        assertEquals(
            listOf(VersionRule("2027-01-project-save", "1.2.0", "1.2.1", LocalDate.of(2027, 1, 15), "Update to 1.2.2 or later.")),
            result.rules.rules,
        )
        assertEquals(emptyList(), result.problems)
    }

    @Test
    fun `a body larger than 64 KB is not valid`() {
        val start = """{"schema": 1, "rules": []}"""
        val body = start + " ".repeat(VersionRulesParser.MAX_BODY_BYTES - start.length + 1)
        assertIs<VersionRulesParse.NotValid>(parse(body))
    }

    @Test
    fun `a body of exactly 64 KB is read`() {
        val start = """{"schema": 1, "rules": []}"""
        valid(start + " ".repeat(VersionRulesParser.MAX_BODY_BYTES - start.length))
    }

    @Test
    fun `a body that is not JSON is not valid`() {
        assertIs<VersionRulesParse.NotValid>(parse("<html>404</html>"))
        assertIs<VersionRulesParse.NotValid>(parse(""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": 1, "rules": []"""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": 1, "rules": []} trailing"""))
    }

    @Test
    fun `a top level that is not an object is not valid`() {
        assertIs<VersionRulesParse.NotValid>(parse("""[{"schema": 1, "rules": []}]"""))
        assertIs<VersionRulesParse.NotValid>(parse("1"))
        assertIs<VersionRulesParse.NotValid>(parse("null"))
    }

    @Test
    fun `no schema or a schema that is not an integer is not valid`() {
        assertIs<VersionRulesParse.NotValid>(parse("""{"rules": []}"""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": "1", "rules": []}"""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": 1.5, "rules": []}"""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": null, "rules": []}"""))
    }

    @Test
    fun `no rules or rules that is not a list is not valid`() {
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": 1, "latest": $latest}"""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": 1, "rules": {}}"""))
        assertIs<VersionRulesParse.NotValid>(parse("""{"schema": 1, "rules": null}"""))
    }

    @Test
    fun `an empty rules list is valid`() {
        val result = valid("""{"schema": 1, "rules": []}""")
        assertNull(result.rules.latest)
        assertEquals(emptyList(), result.rules.rules)
    }

    @Test
    fun `an integer schema that is not 1 is an unknown schema`() {
        assertEquals(VersionRulesParse.UnknownSchema("2"), parse("""{"schema": 2, "rules": []}"""))
        assertEquals(VersionRulesParse.UnknownSchema("0"), parse("""{"schema": 0, "rules": []}"""))
        assertIs<VersionRulesParse.UnknownSchema>(parse("""{"schema": 99999999999999999999, "rules": []}"""))
    }

    @Test
    fun `a file with an unknown schema and a different format is an unknown schema`() {
        assertIs<VersionRulesParse.UnknownSchema>(parse("""{"schema": 2, "rulesV2": []}"""))
    }

    @Test
    fun `a rule that is not valid is ignored, its id goes to the problems, and the other rules apply`() {
        val result = valid(
            """
            {"schema": 1, "rules": [
              {"toVersion": "1.0.0", "stopsOn": "2027-01-15"},
              {"id": "no-to", "stopsOn": "2027-01-15"},
              {"id": "no-stops", "toVersion": "1.0.0"},
              {"id": "bad-date", "toVersion": "1.0.0", "stopsOn": "15.01.2027"},
              {"id": "no-day", "toVersion": "1.0.0", "stopsOn": "2027-02-30"},
              {"id": "long-year", "toVersion": "1.0.0", "stopsOn": "+12027-01-15"},
              {"id": "number-from", "fromVersion": 1, "toVersion": "1.0.0", "stopsOn": "2027-01-15"},
              {"id": 7, "toVersion": "1.0.0", "stopsOn": "2027-01-15"},
              {"id": "number-to", "toVersion": 1, "stopsOn": "2027-01-15"},
              "not an object",
              {"id": "good", "toVersion": "1.0.0", "stopsOn": "2027-01-15"}
            ]}
            """,
        )
        assertEquals(listOf("good"), result.rules.rules.map { it.id })
        assertEquals(10, result.problems.size, result.problems.joinToString("\n"))
        for (id in listOf("no-to", "no-stops", "bad-date", "no-day", "long-year", "number-from", "number-to")) {
            assertTrue(result.problems.any { "'$id'" in it }, "No problem names $id: ${result.problems}")
        }
        assertTrue(result.problems.any { "rule 1 (no id)" in it }, result.problems.toString())
    }

    @Test
    fun `a message that is not a string is ignored, and the rule applies`() {
        val result = valid(
            """
            {"schema": 1, "rules": [
              {"id": "r", "toVersion": "1.0.0", "stopsOn": "2027-01-15", "message": {"text": "x"}}
            ]}
            """,
        )
        assertEquals(listOf(VersionRule("r", null, "1.0.0", LocalDate.of(2027, 1, 15), null)), result.rules.rules)
        assertEquals(1, result.problems.size)
    }

    @Test
    fun `notes or installerUrl that is not a string is ignored, and the rest of latest applies`() {
        val result = valid(
            """
            {"schema": 1, "rules": [],
             "latest": {"version": "1.4.0", "downloadUrl": "https://example.invalid/releases", "installerUrl": 5, "notes": ["a"]}}
            """,
        )
        assertEquals(LatestRelease("1.4.0", "https://example.invalid/releases", null, null), result.rules.latest)
        assertEquals(2, result.problems.size)
    }

    @Test
    fun `a latest that is not an object, or with no version or downloadUrl, is ignored as a whole`() {
        val cases = listOf(
            "\"1.4.0\"",
            "null",
            """{"downloadUrl": "https://example.invalid/releases"}""",
            """{"version": "1.4.0"}""",
            """{"version": 140, "downloadUrl": "https://example.invalid/releases"}""",
        )
        for (case in cases) {
            val result = valid("""{"schema": 1, "rules": [], "latest": $case}""")
            assertNull(result.rules.latest, case)
            assertEquals(1, result.problems.size, case)
        }
    }

    @Test
    fun `unknown fields are ignored, and the known fields are read`() {
        val result = valid(
            """
            {"schema": 1, "future": {"a": 1},
             "latest": {"version": "1.4.0", "downloadUrl": "https://example.invalid/releases", "size": 10},
             "rules": [{"id": "r", "toVersion": "1.0.0", "stopsOn": "2027-01-15", "onlyOn": "Windows 10"}]}
            """,
        )
        assertEquals("1.4.0", result.rules.latest?.version)
        assertEquals(listOf("r"), result.rules.rules.map { it.id })
        assertEquals(emptyList(), result.problems)
    }

    @Test
    fun `two rules with the same id both apply`() {
        val result = valid(
            """
            {"schema": 1, "rules": [
              {"id": "same", "toVersion": "1.0.0", "stopsOn": "2027-01-15"},
              {"id": "same", "toVersion": "1.1.0", "stopsOn": "2027-02-15"}
            ]}
            """,
        )
        assertEquals(listOf("1.0.0", "1.1.0"), result.rules.rules.map { it.toVersion })
    }

    @Test
    fun `a version text that does not start with a number does not make the rule not valid`() {
        val result = valid("""{"schema": 1, "rules": [{"id": "r", "toVersion": "v1", "stopsOn": "2027-01-15"}]}""")
        assertEquals(listOf("r"), result.rules.rules.map { it.id })
        assertEquals(emptyList(), result.problems)
    }
}
