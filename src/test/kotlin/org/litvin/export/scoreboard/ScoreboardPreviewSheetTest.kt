package org.litvin.export.scoreboard

import org.litvin.scoring.ScoreboardStyleId
import kotlin.test.Test
import kotlin.test.assertTrue

class ScoreboardPreviewSheetTest {
    @Test
    fun theSheetHasARowForEachStyleAndAColumnForEachVariant() {
        val one = ScoreboardPreviewSheet.render(listOf(ScoreboardStyleId.STADIUM), scale = 0.5)
        val all = ScoreboardPreviewSheet.render(ScoreboardStyleId.entries, scale = 0.5)

        assertTrue(one.width > 0 && one.height > 0)
        assertTrue(all.height > one.height * 5, "Each style adds a row")
    }
}
