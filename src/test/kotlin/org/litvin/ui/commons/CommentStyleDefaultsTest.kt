package org.litvin.ui.commons

import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
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
}
