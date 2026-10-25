package org.litvin

import org.litvin.points.CommentDefaultsV1
import org.litvin.points.CommentPosition
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.points.components.CommentDispatcher
import org.litvin.points.components.CommentPatch
import org.litvin.points.components.CommentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentDispatcherTest {
    @Test
    fun create_allocatesMonotonicIdsAndUsesProjectDefaultColor() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(CommentState(emptyList(), CommentDefaultsV1("#AA5500"), nextCommentId = 4))

        val first = dispatcher.create(startMs = 1_000, durationMs = 2_000, text = "In")
        dispatcher.delete(4)
        val second = dispatcher.create(startMs = 2_000, durationMs = 2_000, text = "Out")

        assertEquals(CommentV1(4, 1_000, 2_000, "In", "#AA5500"), first)
        assertEquals(5, second?.id)
        assertEquals("#AA5500", second?.colorHex)
    }

    @Test
    fun updateColor_updatesTheCommentAndProjectDefault() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(
            CommentState(
                comments = listOf(CommentV1(1, 0, 1_000, "Serve", "#FFFFFF")),
                defaults = CommentDefaultsV1(),
                nextCommentId = 2,
            ),
        )

        assertTrue(dispatcher.update(1, CommentPatch(colorHex = "#12ab34")))

        assertEquals("#12AB34", dispatcher.state().comments.single().colorHex)
        assertEquals("#12AB34", dispatcher.state().defaults.colorHex)
    }

    @Test
    fun createAndUpdateKeepTheStyle() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(CommentState(emptyList(), CommentDefaultsV1(), nextCommentId = 1))

        val created = dispatcher.create(startMs = 0, durationMs = 1_000, text = "In", style = CommentStyle.PILL)
        assertEquals(CommentStyle.PILL, created?.style)

        assertTrue(dispatcher.update(1, CommentPatch(text = "Out")))
        assertEquals(CommentStyle.PILL, dispatcher.state().comments.single().style, "A patch without a style keeps the style")

        assertTrue(dispatcher.update(1, CommentPatch(style = CommentStyle.CARD)))
        assertEquals(CommentStyle.CARD, dispatcher.state().comments.single().style)
    }

    @Test
    fun rejectsInvalidInputWithoutMutatingCommentState() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(
            CommentState(
                comments = listOf(CommentV1(1, 0, 1_000, "Serve", "#FFFFFF")),
                defaults = CommentDefaultsV1(),
                nextCommentId = 2,
            ),
        )
        val before = dispatcher.state()

        assertNull(dispatcher.create(startMs = -1, durationMs = 1_000, text = "In"))
        assertNull(dispatcher.create(startMs = 0, durationMs = 0, text = "In"))
        assertNull(dispatcher.create(startMs = 0, durationMs = 1_000, text = "   "))
        assertNull(dispatcher.create(startMs = 0, durationMs = 1_000, text = "In", colorHex = "blue"))
        assertFalse(dispatcher.update(99, CommentPatch(text = "Missing")))
        assertFalse(dispatcher.update(1, CommentPatch(colorHex = "blue")))

        assertEquals(before, dispatcher.state())
    }

    @Test
    fun updatePosition_movesTheCommentAndTheDefaultPositionRemovesIt() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(CommentState(listOf(CommentV1(1, 0, 1_000, "Serve", "#FFFFFF")), CommentDefaultsV1(), 2))

        assertTrue(dispatcher.update(1, CommentPatch(position = CommentPosition(0.25, 0.3))))
        assertEquals(CommentPosition(0.25, 0.3), dispatcher.state().comments.single().position)

        assertTrue(dispatcher.update(1, CommentPatch(text = "Return")), "Other changes keep the position")
        assertEquals(CommentPosition(0.25, 0.3), dispatcher.state().comments.single().position)

        assertTrue(dispatcher.update(1, CommentPatch(position = CommentPosition(1.5, Double.NaN))), "A position that is not a number is ignored")
        assertEquals(CommentPosition(0.25, 0.3), dispatcher.state().comments.single().position)

        assertTrue(dispatcher.update(1, CommentPatch(position = CommentPosition.DEFAULT)))
        assertNull(dispatcher.state().comments.single().position, "A comment at the default position has no position")
    }

    @Test
    fun sizeAndOpacity_areKeptInTheirRanges() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(CommentState(emptyList(), CommentDefaultsV1(), 1))

        val created = dispatcher.create(startMs = 0, durationMs = 1_000, text = "Big", sizePercent = 500, opacityPercent = 0)!!
        assertEquals(CommentV1.MAX_SIZE_PERCENT, created.sizePercent)
        assertEquals(CommentV1.MIN_OPACITY_PERCENT, created.opacityPercent)

        assertTrue(dispatcher.update(created.id, CommentPatch(sizePercent = 150, opacityPercent = 70)))
        val updated = dispatcher.state().comments.single()
        assertEquals(150, updated.sizePercent)
        assertEquals(70, updated.opacityPercent)

        assertTrue(dispatcher.update(created.id, CommentPatch(text = "Small")), "Other changes keep the size and the opacity")
        assertEquals(150 to 70, dispatcher.state().comments.single().let { it.sizePercent to it.opacityPercent })
    }

    @Test
    fun rotation_isKeptInItsRange_andOtherChangesKeepIt() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(CommentState(emptyList(), CommentDefaultsV1(), 1))

        val created = dispatcher.create(startMs = 0, durationMs = 1_000, text = "Turn", rotationDeg = 200)!!
        assertEquals(-160, created.rotationDeg)

        assertTrue(dispatcher.update(created.id, CommentPatch(rotationDeg = 15)))
        assertEquals(15, dispatcher.state().comments.single().rotationDeg)

        assertTrue(dispatcher.update(created.id, CommentPatch(position = CommentPosition(0.2, 0.3), sizePercent = 120)))
        assertEquals(15, dispatcher.state().comments.single().rotationDeg, "A move and a resize keep the rotation")
    }
}
