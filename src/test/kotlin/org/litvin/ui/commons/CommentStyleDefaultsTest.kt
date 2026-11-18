package org.litvin.ui.commons

import org.litvin.points.CommentAnimation
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.points.Replays
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentStyleDefaultsTest {
    private val node = InMemoryPreferencesProvider().node("application")

    @Test
    fun `no saved look gives Outline at 100 percent`() {
        assertEquals(CommentLook(CommentStyle.OUTLINE, 100, 100), PreferencesCommentStyleDefaults(node).load())
    }

    @Test
    fun `the last saved look is read back by a new instance`() {
        PreferencesCommentStyleDefaults(node).save(CommentLook(CommentStyle.CARD, 150, 80))
        PreferencesCommentStyleDefaults(node).save(CommentLook(CommentStyle.PILL, 70, 40))

        assertEquals(CommentLook(CommentStyle.PILL, 70, 40), PreferencesCommentStyleDefaults(node).load())
    }

    @Test
    fun `an unknown saved style gives Outline`() {
        node.put("default-comment-style", "NEON")

        assertEquals(CommentStyle.OUTLINE, PreferencesCommentStyleDefaults(node).load().style)
    }

    @Test
    fun `a saved size or opacity outside its range moves into the range`() {
        node.putInt("default-comment-size-percent", 900)
        node.putInt("default-comment-opacity-percent", 0)

        val look = PreferencesCommentStyleDefaults(node).load()

        assertEquals(CommentV1.MAX_SIZE_PERCENT, look.sizePercent)
        assertEquals(CommentV1.MIN_OPACITY_PERCENT, look.opacityPercent)
    }

    @Test
    fun `the last animation is read back, and no or an unknown saved animation gives None`() {
        assertEquals(CommentAnimation.NONE, PreferencesCommentStyleDefaults(node).load().animation)

        PreferencesCommentStyleDefaults(node).save(CommentLook(CommentStyle.CARD, 120, 90, CommentAnimation.FADE))
        assertEquals(CommentLook(CommentStyle.CARD, 120, 90, CommentAnimation.FADE), PreferencesCommentStyleDefaults(node).load())

        node.put("default-comment-animation", "SPIN")
        assertEquals(CommentAnimation.NONE, PreferencesCommentStyleDefaults(node).load().animation)
    }

    @Test
    fun `a new replay comment has 3 s until the user saves another time`() {
        assertEquals(3_000L, PreferencesCommentStyleDefaults(node).loadReplayDurationMs())

        PreferencesCommentStyleDefaults(node).saveReplayDurationMs(4_500L)

        assertEquals(4_500L, PreferencesCommentStyleDefaults(node).loadReplayDurationMs())
    }

    @Test
    fun `a saved replay comment time outside its range moves into the range`() {
        node.putLong("default-replay-comment-duration-ms", 0L)
        assertEquals(Replays.MIN_COMMENT_MS.toLong(), PreferencesCommentStyleDefaults(node).loadReplayDurationMs())

        node.putLong("default-replay-comment-duration-ms", 999_999L)
        assertEquals(Replays.MAX_COMMENT_MS.toLong(), PreferencesCommentStyleDefaults(node).loadReplayDurationMs())
    }

    @Test
    fun `a saved look does not change the replay comment time`() {
        PreferencesCommentStyleDefaults(node).saveReplayDurationMs(5_000L)
        PreferencesCommentStyleDefaults(node).save(CommentLook(CommentStyle.CARD, 120, 90))

        assertEquals(5_000L, PreferencesCommentStyleDefaults(node).loadReplayDurationMs())
    }
}
