package org.litvin.ui.commons

import org.litvin.points.CommentStyle
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentStyleDefaultsTest {
    private val node = InMemoryPreferencesProvider().node("application")

    @Test
    fun `no saved style gives Outline`() {
        assertEquals(CommentStyle.OUTLINE, PreferencesCommentStyleDefaults(node).load())
    }

    @Test
    fun `the last saved style is read back by a new instance`() {
        PreferencesCommentStyleDefaults(node).save(CommentStyle.CARD)
        PreferencesCommentStyleDefaults(node).save(CommentStyle.PILL)

        assertEquals(CommentStyle.PILL, PreferencesCommentStyleDefaults(node).load())
    }

    @Test
    fun `an unknown saved style gives Outline`() {
        node.put("default-comment-style", "NEON")

        assertEquals(CommentStyle.OUTLINE, PreferencesCommentStyleDefaults(node).load())
    }
}
