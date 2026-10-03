package org.litvin.license

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EffectiveExpiryTest {
    private val buildExpiry = LocalDate.of(2027, 4, 3)
    private val latest = LatestRelease("1.4.0", "https://example.invalid/releases")

    private fun rule(id: String, toVersion: String, stopsOn: LocalDate, message: String? = null) =
        VersionRule(id, fromVersion = null, toVersion = toVersion, stopsOn = stopsOn, message = message)

    private fun expiry(appVersion: String, vararg rules: VersionRule) =
        EffectiveExpiry.of(appVersion, VersionRules(latest, rules.toList()), buildExpiry)

    @Test
    fun `with no rules file the build expiry gives the expiry`() {
        assertEquals(EffectiveExpiry(buildExpiry, null), EffectiveExpiry.of("1.2.0", null, buildExpiry))
    }

    @Test
    fun `with no matching rule the build expiry gives the expiry`() {
        assertEquals(EffectiveExpiry(buildExpiry, null), expiry("1.2.0"))
    }

    @Test
    fun `a rule that is earlier gives the expiry`() {
        val early = rule("early", "1.2.0", LocalDate.of(2027, 1, 15), "Security problem")
        val result = expiry("1.2.0", early)
        assertEquals(LocalDate.of(2027, 1, 15), result.date)
        assertSame(early, result.rule)
        assertEquals("Security problem", result.message)
    }

    @Test
    fun `a rule that is later has no effect, so the file cannot make the expiry later`() {
        assertEquals(EffectiveExpiry(buildExpiry, null), expiry("1.2.0", rule("late", "1.2.0", LocalDate.of(2028, 1, 1))))
    }

    @Test
    fun `a rule for a different version has no effect`() {
        assertEquals(EffectiveExpiry(buildExpiry, null), expiry("1.2.0", rule("old", "1.1.0", LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun `a rule that matches latest_version has no effect`() {
        assertEquals(EffectiveExpiry(buildExpiry, null), expiry("1.2.0", rule("wrong", "1.4.0", LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun `the earliest of the matching rules gives the expiry`() {
        val later = rule("later", "1.2.0", LocalDate.of(2027, 2, 1))
        val earlier = rule("earlier", "1.3.0", LocalDate.of(2026, 12, 1))
        assertSame(earlier, expiry("1.2.0", later, earlier).rule)
    }

    @Test
    fun `a rule with the same date as the build expiry gives the expiry, so that its message shows`() {
        val same = rule("same", "1.2.0", buildExpiry, "Please update")
        assertEquals(EffectiveExpiry(buildExpiry, same), expiry("1.2.0", same))
    }

    @Test
    fun `a version that is not MAJOR_MINOR_PATCH compares as its numbers`() {
        val stop = rule("stop", "1.3.0", LocalDate.of(2027, 1, 15))
        assertSame(stop, expiry("1.3-SNAPSHOT", stop).rule)
        // A version with no number is 0.0.0, so a rule with no fromVersion stops it.
        assertSame(stop, expiry("development", stop).rule)
        assertNull(expiry("1.3.1-beta", stop).rule)
    }

    @Test
    fun `the version works at 23_59_59 UTC on the day before the expiry date and stops at 00_00 UTC on it`() {
        val expiry = EffectiveExpiry(LocalDate.of(2027, 3, 30), null)
        assertEquals(Instant.parse("2027-03-30T00:00:00Z"), expiry.moment)
        assertFalse(expiry.isExpired(Instant.parse("2027-03-29T23:59:59Z")))
        assertTrue(expiry.isExpired(Instant.parse("2027-03-30T00:00:00Z")))
    }

    @Test
    fun `the warning starts exactly 30 x 24 hours before the expiry moment`() {
        val expiry = EffectiveExpiry(LocalDate.of(2027, 3, 30), null)
        assertEquals(Instant.parse("2027-02-28T00:00:00Z"), expiry.warningStart)
        assertFalse(expiry.isWarning(expiry.warningStart - Duration.ofSeconds(1)))
        assertTrue(expiry.isWarning(expiry.warningStart))
        assertTrue(expiry.isWarning(expiry.moment))
    }

    @Test
    fun `the moment shows in the local time zone with the hour`() {
        val moment = Instant.parse("2027-03-30T00:00:00Z")
        assertEquals("29 March 2027, 16:00", ExpiryMomentFormat.format(moment, ZoneOffset.ofHours(-8)))
        assertEquals("30 March 2027, 02:00", ExpiryMomentFormat.format(moment, ZoneId.of("Europe/Berlin")))
        assertEquals("30 March 2027, 00:00", ExpiryMomentFormat.format(moment, ZoneOffset.UTC))
    }

    @Test
    fun `the log gets the rule id and the date when the effective expiry changes`() {
        val lines = mutableListOf<String>()
        val log = ExpiryLog(lines::add)
        val now = Instant.parse("2026-11-01T00:00:00Z")
        val rule = rule("security-1", "1.2.0", LocalDate.of(2027, 1, 15))

        log.record(EffectiveExpiry(buildExpiry, null), now)
        log.record(EffectiveExpiry(buildExpiry, null), now)
        log.record(EffectiveExpiry(rule.stopsOn, rule), now)
        log.record(EffectiveExpiry(rule.stopsOn, rule), now)

        assertEquals(
            listOf("Effective expiry: 2027-04-03 (build expiry)", "Effective expiry: 2027-01-15 (rule security-1)"),
            lines,
        )
    }

    @Test
    fun `the log gets the rule id and the date one time when the rule stops the version`() {
        val lines = mutableListOf<String>()
        val log = ExpiryLog(lines::add)
        val rule = rule("security-1", "1.2.0", LocalDate.of(2027, 1, 15))
        val expiry = EffectiveExpiry(rule.stopsOn, rule)

        log.record(expiry, Instant.parse("2027-01-14T23:59:59Z"))
        log.record(expiry, Instant.parse("2027-01-15T00:00:00Z"))
        log.record(expiry, Instant.parse("2027-01-15T01:00:00Z"))

        assertEquals(
            listOf("Effective expiry: 2027-01-15 (rule security-1)", "Rule security-1 stopped the version on 2027-01-15"),
            lines,
        )
    }

    @Test
    fun `the build expiry writes no stop line for a rule`() {
        val lines = mutableListOf<String>()
        ExpiryLog(lines::add).record(EffectiveExpiry(buildExpiry, null), Instant.parse("2027-05-01T00:00:00Z"))
        assertEquals(listOf("Effective expiry: 2027-04-03 (build expiry)"), lines)
    }
}
