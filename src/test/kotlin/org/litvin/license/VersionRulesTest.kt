package org.litvin.license

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionRulesTest {
    private fun rule(id: String, fromVersion: String?, toVersion: String) =
        VersionRule(id, fromVersion, toVersion, LocalDate.of(2027, 1, 15))

    @Test
    fun `a rule matches the versions from fromVersion to toVersion, both ends included`() {
        val rule = rule("r", "1.2.0", "1.2.1")
        assertFalse(rule.matches(Version.parse("1.1.9")))
        assertTrue(rule.matches(Version.parse("1.2.0")))
        assertTrue(rule.matches(Version.parse("1.2.1-beta")))
        assertTrue(rule.matches(Version.parse("1.2.1")))
        assertFalse(rule.matches(Version.parse("1.2.2")))
    }

    @Test
    fun `a rule with no fromVersion matches all versions up to toVersion`() {
        val rule = rule("r", null, "1.2.1")
        assertTrue(rule.matches(Version.ZERO))
        assertTrue(rule.matches(Version.parse("development")))
        assertTrue(rule.matches(Version.parse("1.2.1")))
        assertFalse(rule.matches(Version.parse("1.2.2")))
    }

    @Test
    fun `a development version compares as its numbers`() {
        assertTrue(rule("r", null, "1.3.0").matches(Version.parse("1.3-SNAPSHOT")))
        assertFalse(rule("r", null, "1.2.9").matches(Version.parse("1.3-SNAPSHOT")))
    }

    @Test
    fun `matchingRules gives only the rules for the app version`() {
        val forOld = rule("old", null, "1.1.0")
        val forApp = rule("app", "1.2.0", "1.2.1")
        val rules = VersionRules(latest = null, rules = listOf(forOld, forApp))
        assertEquals(listOf(forApp), rules.matchingRules("1.2.1"))
        assertEquals(emptyList(), rules.matchingRules("1.3.0"))
    }

    @Test
    fun `a rule that matches latest_version is ignored`() {
        val wrong = rule("wrong", null, "1.4.0")
        val correct = rule("correct", null, "1.2.1")
        val rules = VersionRules(LatestRelease("1.4.0", "https://example.invalid/releases"), listOf(wrong, correct))
        assertEquals(listOf(correct), rules.matchingRules("1.2.0"))
    }

    @Test
    fun `a rule for a different version has no effect`() {
        val rules = VersionRules(LatestRelease("1.4.0", "https://example.invalid/releases"), listOf(rule("r", "1.0.0", "1.1.0")))
        assertEquals(emptyList(), rules.matchingRules("1.2.0"))
    }
}
