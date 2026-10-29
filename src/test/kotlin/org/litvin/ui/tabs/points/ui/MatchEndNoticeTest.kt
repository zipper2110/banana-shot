package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchEndNoticeTest {
    @Test
    fun noticeShowsOnlyWhenPointsAfterTheMatchHaveAWinner() {
        val notice = MatchEndNotice(onChangeFormat = {}, onGoToMatchPoint = {})

        notice.show(matchPointNumber = 142, scoredAfter = 0)
        assertFalse(notice.isVisible)

        notice.show(matchPointNumber = 142, scoredAfter = 12)
        assertTrue(notice.isVisible)
        assertTrue("142" in notice.text, notice.text)
        assertTrue("12 points" in notice.text, notice.text)

        notice.show(matchPointNumber = 142, scoredAfter = 0)
        assertFalse(notice.isVisible)
    }

    @Test
    fun buttonsCallTheirActions() {
        var changed = 0
        var went = 0
        val notice = MatchEndNotice(onChangeFormat = { changed++ }, onGoToMatchPoint = { went++ })

        notice.changeFormatButton.doClick()
        notice.goToMatchPointButton.doClick()

        assertEquals(1, changed)
        assertEquals(1, went)
    }

    @Test
    fun buttonsStackWhenTheColumnIsNarrow() {
        val notice = MatchEndNotice(onChangeFormat = {}, onGoToMatchPoint = {})
        notice.show(matchPointNumber = 3, scoredAfter = 1)

        assertTrue(notice.heightForWidth(200) > notice.heightForWidth(1_000))
    }
}
