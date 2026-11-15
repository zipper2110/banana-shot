package org.litvin.scoring

import org.litvin.shared.util.PaidEdition
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.help.HelpCatalog
import org.litvin.ui.help.HelpPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Advanced scoring is a paid feature (docs/paid-features.md). The free version uses basic mode. */
class AdvancedScoringEditionTest {
    private val advanced = ScoreV1(sport = Sport.TENNIS, scoringMode = ScoringMode.ADVANCED)

    @Test
    fun `the free version uses basic mode for a project in advanced mode and keeps the stored mode`() =
        PaidEdition.with(paid = false) {
            assertFalse(ScoringMode.ADVANCED.available)
            assertEquals(ScoringMode.BASIC, ScoringMode.ADVANCED.orAvailable())
            assertFalse(advanced.isAdvanced)
            assertEquals(ScoringMode.ADVANCED, advanced.scoringMode)
        }

    @Test
    fun `the paid version uses advanced mode`() = PaidEdition.with(paid = true) {
        assertTrue(ScoringMode.ADVANCED.available)
        assertEquals(ScoringMode.ADVANCED, ScoringMode.ADVANCED.orAvailable())
        assertTrue(advanced.isAdvanced)
    }

    @Test
    fun `basic mode is in both versions`() {
        PaidEdition.with(paid = false) { assertTrue(ScoringMode.BASIC.available) }
        PaidEdition.with(paid = true) { assertTrue(ScoringMode.BASIC.available) }
    }

    @Test
    fun `the free version has no help page and no help text about advanced scoring`() = PaidEdition.with(paid = false) {
        assertFalse(HelpPage.ADVANCED_SCORING in HelpPage.shown)
        assertEquals(HelpPage.entries.size - 1, HelpPage.shown.size)

        val points = HelpCatalog.content(HelpPage.POINTS)
        val shortcuts = points.shortcuts.map { it.shortcut }
        assertFalse(AppShortcuts.NEXT_PACK in shortcuts)
        assertFalse(AppShortcuts.PREVIOUS_PACK in shortcuts)
        val texts = HelpPage.shown.flatMap { page ->
            HelpCatalog.content(page).let { listOf(it.summary) + it.workflow + it.actions + it.goodToKnow + it.shortcuts.map { s -> s.action } }
        }
        // The Export help uses "Advanced" for the export quality settings, so the check does not look for that word.
        texts.forEach { text ->
            assertFalse(text.contains("scoring mode", ignoreCase = true), text)
            assertFalse(text.contains("serve fault", ignoreCase = true), text)
        }
    }

    @Test
    fun `the paid version has the help page and the help texts about advanced scoring`() = PaidEdition.with(paid = true) {
        assertEquals(HelpPage.entries, HelpPage.shown)
        val shortcuts = HelpCatalog.content(HelpPage.POINTS).shortcuts.map { it.shortcut }
        assertTrue(AppShortcuts.NEXT_PACK in shortcuts)
        assertTrue(AppShortcuts.PREVIOUS_PACK in shortcuts)
        assertTrue(HelpCatalog.content(HelpPage.POINTS).actions.any { it.contains("scoring mode") })
    }
}
