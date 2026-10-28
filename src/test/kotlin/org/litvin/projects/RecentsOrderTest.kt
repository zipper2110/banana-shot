package org.litvin.projects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.litvin.i18n.AppLanguage
import org.litvin.projects.RecentsProvider.RecentEntry
import java.time.Instant

class RecentsOrderTest {
    private fun entry(name: String, path: String = name, at: Instant? = null) = RecentEntry(path, name, at)

    @Test
    fun `the newest open comes first, and the name decides only for the same time`() {
        val new = entry("Zeta", at = Instant.parse("2026-10-29T10:00:00Z"))
        val names = listOf(entry("Bruno"), new, entry("Ángel")).sortedWith(RecentsProvider.order(AppLanguage.SPANISH_SPAIN))
        assertEquals(listOf("Zeta", "Ángel", "Bruno"), names.map { it.name })
    }

    @Test
    fun `equal names keep a fixed order`() {
        val entries = listOf(entry("angel", "b"), entry("Angel", "c"), entry("angel", "a"))
        val order = RecentsProvider.order(AppLanguage.ENGLISH)
        assertEquals(listOf("Angel/c", "angel/a", "angel/b"), entries.sortedWith(order).map { "${it.name}/${it.path}" })
        assertEquals(entries.sortedWith(order), entries.reversed().sortedWith(order))
    }

    @Test
    fun `digits, emoji, and other scripts do not cause an error`() {
        val names = listOf(entry("決勝 🎾"), entry("2026 Final"), entry("Финал"), entry("Bruno"), entry("🎾"))
            .sortedWith(RecentsProvider.order(AppLanguage.SPANISH_SPAIN)).map { it.name }
        assertEquals(5, names.size)
        assertEquals("Bruno", names.first { it.first().isLetter() && it.first().code < 0x250 })
    }
}
