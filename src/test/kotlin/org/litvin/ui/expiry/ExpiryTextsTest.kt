package org.litvin.ui.expiry

import org.litvin.AppInfo
import org.litvin.license.EffectiveExpiry
import org.litvin.license.LatestRelease
import org.litvin.license.VersionRules
import org.litvin.license.check.CheckState
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E8-S4 and the texts of build-expiry-spec.md, "User interface". */
class ExpiryTextsTest {
    private fun rules(latest: String?) = VersionRules(latest?.let { LatestRelease(it, "https://example.invalid/releases") }, emptyList())

    @Test
    fun `the check state texts`() {
        assertEquals("Checking…", ExpiryTexts.checkState(CheckState.CHECKING))
        assertEquals(
            "${AppInfo.NAME} cannot connect to the update server. It checks again every minute. " +
                "If the date is wrong, correct the clock and connect to the internet.",
            ExpiryTexts.checkState(CheckState.NO_CONNECTION),
        )
        assertNull(ExpiryTexts.checkState(CheckState.STILL_EXPIRED))
        assertNull(ExpiryTexts.checkState(CheckState.NONE))
        assertEquals("${AppInfo.NAME} cannot connect to the update server.", ExpiryTexts.clockBehindCheckState(CheckState.NO_CONNECTION))
    }

    @Test
    fun `the texts use AppInfo NAME and the source has no fixed name`() {
        assertTrue(ExpiryTexts.EXPIRED_DEFAULT.contains(AppInfo.NAME))
        assertTrue(ExpiryTexts.CLOCK_BEHIND.contains(AppInfo.NAME))
        val source = File("src/main/kotlin/org/litvin/ui/expiry").listFiles().orEmpty().filter { it.name.endsWith(".kt") }
        source.forEach { file -> assertFalse(file.readText().contains("BananaShot"), "${file.name} must use AppInfo.NAME") }
    }

    @Test
    fun `the new version line shows only for a newer latest version`() {
        assertEquals("Version 1.4.0 is available.", ExpiryTexts.newVersion(rules("1.4.0"), "1.2.0"))
        assertNull(ExpiryTexts.newVersion(rules("1.2.0"), "1.2.0"))
        assertNull(ExpiryTexts.newVersion(rules("1.1.0"), "1.2.0"))
        assertNull(ExpiryTexts.newVersion(rules(null), "1.2.0"))
        assertNull(ExpiryTexts.newVersion(null, "1.2.0"))
    }

    @Test
    fun `the warning shows the moment in local time with the hour`() {
        val expiry = EffectiveExpiry(LocalDate.of(2027, 3, 30), null)

        assertEquals(
            "This version works until 30 March 2027, 02:00. Update to the new version.",
            ExpiryTexts.warning(expiry, ZoneId.of("Europe/Berlin")),
        )
    }

    @Test
    fun `the wrong clock text gives the days and the direction`() {
        assertEquals(
            "The clock of this computer is 2 days ahead. Correct the clock. If you do not, ${AppInfo.NAME} can stop while the computer is offline.",
            ExpiryTexts.wrongClock(Duration.ofHours(36), 2),
        )
        assertTrue(ExpiryTexts.wrongClock(Duration.ofHours(-25), 1).startsWith("The clock of this computer is 1 day behind."))
    }

    @Test
    fun `the date that the app used and the download progress`() {
        assertEquals(
            "The date that ${AppInfo.NAME} used: 2 November 2026, 10:00.",
            ExpiryTexts.dateUsed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC),
        )
        assertEquals("Downloading… 50%", ExpiryTexts.downloading(50, 100))
        assertEquals("Downloading… 3 MB", ExpiryTexts.downloading(3L * 1024 * 1024, null))
    }
}
