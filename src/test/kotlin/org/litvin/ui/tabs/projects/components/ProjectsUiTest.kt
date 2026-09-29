package org.litvin.ui.tabs.projects.components

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsUiTest {
    private val font = ProjectsUi.font(12f)

    @Test
    fun aLongPathLosesTheStartOfTheFolderAndKeepsTheFileName() {
        val path = "D:\\a\\very\\long\\folder\\name\\that\\goes\\on\\PXL_20260826_070000100.mp4"
        val maxWidth = ProjectsUi.textWidth("PXL_20260826_070000100.mp4", font) + 40f

        val shown = ProjectsUi.ellipsizeStart(path, font, maxWidth)

        assertTrue(shown.startsWith("…"), shown)
        assertTrue(shown.endsWith("PXL_20260826_070000100.mp4"), shown)
        assertTrue(ProjectsUi.textWidth(shown, font) <= maxWidth)
    }

    @Test
    fun aShortPathStaysComplete() {
        assertEquals("D:\\video\\a.mp4", ProjectsUi.ellipsizeStart("D:\\video\\a.mp4", font, 500f))
    }

    @Test
    fun aWrappedPathBreaksAfterAFolderSeparator() {
        val path = "D:\\video\\2026\\september\\PXL_20260920_153210877.mp4"
        val maxWidth = ProjectsUi.textWidth("PXL_20260920_153210877.mp4", font) + 30f

        val lines = ProjectsUi.wrap(path, font, maxWidth)

        assertEquals(listOf("D:\\video\\2026\\september\\", "PXL_20260920_153210877.mp4"), lines)
        lines.forEach { assertTrue(ProjectsUi.textWidth(it, font) <= maxWidth, it) }
    }

    @Test
    fun aWordLongerThanALineBreaksAtAnyCharacter() {
        val maxWidth = ProjectsUi.textWidth("PXL_2026", font) + 1f

        val lines = ProjectsUi.wrap("PXL_20260920_153210877", font, maxWidth)

        assertEquals("PXL_20260920_153210877", lines.joinToString(""))
        lines.forEach { assertTrue(ProjectsUi.textWidth(it, font) <= maxWidth, it) }
    }
}
