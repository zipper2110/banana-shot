package org.litvin.ui.tabs.points.replay

import org.litvin.points.PointV1
import org.litvin.points.ReplayV1
import org.litvin.points.ReplayZoom
import org.litvin.points.Replays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayEditorModelTest {
    private val point = PointV1("p1", 10_000, 14_000)

    @Test
    fun theReplayCommentsGetNewIdsStayInsideThePartAndCanBeChangedAndDeleted() {
        val model = ReplayEditorModel(point)
        model.add(12_000)
        val draft = org.litvin.points.CommentV1(id = 0, startMs = 99_000, durationMs = 2_000, text = "Lob", colorHex = "#FFFFFF")

        val first = model.addComment(draft)
        val second = model.addComment(draft.copy(startMs = 11_000))
        assertEquals(listOf(1, 2), model.comments.map { it.id })
        // The start goes to the part end.
        assertEquals(13_500, first?.startMs)
        assertEquals(11_000, second?.startMs)

        model.moveComment(2, 12_000)
        assertEquals(12_000, model.comment(2)?.startMs)
        model.placeComment(2, org.litvin.points.CommentPosition(0.2, 0.3), sizePercent = 150, rotationDeg = 10)
        assertEquals(org.litvin.points.CommentPosition(0.2, 0.3), model.comment(2)?.position)
        model.updateComment(model.comment(2)!!.copy(text = "Great lob"))
        assertEquals("Great lob", model.comment(2)?.text)

        // An edge that moves over a comment takes it along.
        model.setEnd(11_500)
        assertEquals(listOf(11_500, 11_500), model.comments.map { it.startMs })

        model.deleteComment(1)
        assertEquals(listOf(2), model.comments.map { it.id })
        assertEquals(3, model.addComment(draft)?.id)

        assertTrue(model.selected!!.showPointComments)
        model.setShowPointComments(false)
        assertFalse(model.selected!!.showPointComments)
    }

    @Test
    fun theStillFrameTurnsOnAtThePlayheadMovesInsideThePartAndTurnsOff() {
        val model = ReplayEditorModel(point)
        model.add(12_000)
        assertNull(model.selected?.freeze)

        model.setFreeze(true, playheadMs = 11_000)
        assertEquals(org.litvin.points.ReplayFreeze(11_000, Replays.DEFAULT_FREEZE_MS), model.selected?.freeze)

        model.setFreezeAt(99_000)
        assertEquals(13_500, model.selected?.freeze?.atMs)
        model.setFreezeDuration(1_500)
        assertEquals(1_500, model.selected?.freeze?.durationMs)

        // An edge that moves over the still frame takes it along.
        model.setEnd(12_500)
        assertEquals(12_500, model.selected?.freeze?.atMs)

        model.setFreeze(false, playheadMs = 0)
        assertNull(model.selected?.freeze)
    }

    @Test
    fun aStillFrameWithThePlayheadOutsideThePartIsInTheMiddle() {
        val model = ReplayEditorModel(point)
        model.add(12_000)
        model.setFreeze(true, playheadMs = 1_000)
        assertEquals(12_000, model.selected?.freeze?.atMs)
    }

    @Test
    fun theLabelIsTheTypedTextOrTheDefaultOfThePlace() {
        val model = ReplayEditorModel(point)
        model.add(11_000)
        model.add(12_000)
        assertEquals("Replay 2", model.selectedLabel)

        model.setLabel("Ace")
        assertEquals("Ace", model.selected?.label)
        assertEquals("Ace", model.selectedLabel)

        model.setLabel(" ")
        assertNull(model.selected?.label)
        assertEquals("Replay 2", model.selectedLabel)
    }

    @Test
    fun aDefaultLabelFollowsTheReplayWhenItMoves() {
        val model = ReplayEditorModel(point)
        model.add(11_000)
        model.add(12_000)

        model.moveSelected(-1)

        assertEquals(0, model.selectedIndex)
        assertEquals("Replay", model.selectedLabel)
        model.select(1)
        assertEquals("Replay 2", model.selectedLabel)
    }

    @Test
    fun aNewReplayIsAroundThePlayheadAndSelected() {
        val model = ReplayEditorModel(point)
        assertEquals(-1, model.selectedIndex)
        assertFalse(model.changed)

        assertTrue(model.add(12_000))

        assertEquals(0, model.selectedIndex)
        assertEquals(ReplayV1(10_500, 13_500), model.selected)
        assertTrue(model.changed)
    }

    @Test
    fun theEdgesStayInsideTheWindowAndKeepTheMinimumLength() {
        val model = ReplayEditorModel(point)
        model.add(12_000)

        model.setStart(1_000)
        assertEquals(Replays.windowStartMs(point), model.selected?.startMs)

        model.setStart(13_400)
        assertEquals(13_500 - Replays.MIN_PART_MS, model.selected?.startMs)

        model.setEnd(99_000)
        assertEquals(Replays.windowEndMs(point), model.selected?.endMs)

        model.setWhole()
        assertEquals(10_000, model.selected?.startMs)
        assertEquals(14_000, model.selected?.endMs)
    }

    @Test
    fun theValuesStayInsideTheLimits() {
        val model = ReplayEditorModel(point)
        model.add(12_000)

        model.setSpeed(5.0)

        assertEquals(Replays.MAX_SPEED, model.selected?.speed)
    }

    @Test
    fun theZoomFactorKeepsTheCenterOfTheFrame() {
        val model = ReplayEditorModel(point)
        model.add(12_000)

        model.setZoomFactor(2.0)
        assertEquals(ReplayZoom(0.25, 0.25, 0.5), model.selected?.zoom)
        assertEquals(2.0, model.zoomFactor)

        model.setZoom(ReplayZoom(0.1, 0.2, 0.5))
        model.setZoomFactor(4.0)
        val zoom = model.selected?.zoom!!
        assertEquals(0.225, zoom.x, 1e-9)
        assertEquals(0.325, zoom.y, 1e-9)
        assertEquals(0.25, zoom.width, 1e-9)

        model.setZoomFactor(1.0)
        assertNull(model.selected?.zoom)
        assertEquals(1.0, model.zoomFactor)
    }

    @Test
    fun deleteSelectsTheReplayBefore() {
        val saved = point.copy(replays = listOf(ReplayV1(10_000, 11_000), ReplayV1(12_000, 13_000)))
        val model = ReplayEditorModel(saved, selectedIndex = 1)
        assertFalse(model.changed)

        model.deleteSelected()
        assertEquals(0, model.selectedIndex)
        assertEquals(listOf(ReplayV1(10_000, 11_000)), model.replays)

        model.deleteSelected()
        assertEquals(-1, model.selectedIndex)
        assertTrue(model.replays.isEmpty())
        assertTrue(model.changed)
    }

    @Test
    fun aMoveChangesThePlayOrderAndKeepsTheSelection() {
        val first = ReplayV1(10_000, 11_000)
        val second = ReplayV1(12_000, 13_000)
        val third = ReplayV1(11_000, 12_000)
        val model = ReplayEditorModel(point.copy(replays = listOf(first, second, third)), selectedIndex = 0)
        assertFalse(model.canMove(-1))

        model.moveSelected(1)
        assertEquals(listOf(second, first, third), model.replays)
        assertEquals(1, model.selectedIndex)
        assertTrue(model.changed)

        model.moveSelected(1)
        assertEquals(listOf(second, third, first), model.replays)
        assertFalse(model.canMove(1))
        model.moveSelected(1)
        assertEquals(listOf(second, third, first), model.replays, "the last replay cannot move later")

        model.moveSelected(-2)
        assertEquals(listOf(first, second, third), model.replays)
        assertEquals(0, model.selectedIndex)
    }

    @Test
    fun theAddedTimeCountsEachReplayOnce() {
        val saved = point.copy(replays = listOf(ReplayV1(10_000, 11_000, speed = 0.5), ReplayV1(11_000, 12_000, speed = 1.0)))
        assertEquals(3_000, ReplayEditorModel(saved).addedMs)
    }
}
