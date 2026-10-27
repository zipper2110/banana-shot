package org.litvin

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayFreeze
import org.litvin.points.ReplayV1
import org.litvin.points.ReplayZoom
import org.litvin.points.Replays
import org.litvin.points.components.PointsDispatcher
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReplaysTest {
    private val point = PointV1(id = "P", startMs = 10_000, endMs = 20_000)

    @Test
    fun `a new replay is 1,5 s before and after the playhead`() {
        assertEquals(ReplayV1(startMs = 13_500, endMs = 16_500), Replays.newAt(point, 15_000))
    }

    @Test
    fun `a new replay near the window edge keeps its length inside the window`() {
        assertEquals(ReplayV1(startMs = 7_000, endMs = 10_000), Replays.newAt(point, 7_200))
        assertEquals(ReplayV1(startMs = 20_000, endMs = 23_000), Replays.newAt(point, 22_900))
    }

    @Test
    fun `the window does not start before the video`() {
        val early = PointV1(id = "E", startMs = 1_000, endMs = 5_000)
        assertEquals(0, Replays.windowStartMs(early))
        assertEquals(ReplayV1(startMs = 0, endMs = 3_000), Replays.newAt(early, 500))
    }

    @Test
    fun `a changed point cuts its replays to the new window`() {
        val replays = listOf(
            ReplayV1(startMs = 8_000, endMs = 12_000),
            ReplayV1(startMs = 18_000, endMs = 22_000),
        )
        val moved = point.copy(startMs = 12_000, endMs = 18_000, replays = replays)
        assertEquals(
            listOf(ReplayV1(startMs = 9_000, endMs = 12_000), ReplayV1(startMs = 18_000, endMs = 21_000)),
            Replays.fitTo(moved),
        )
    }

    @Test
    fun `a replay shorter than 0,5 s after the cut is removed`() {
        val moved = point.copy(startMs = 16_000, replays = listOf(ReplayV1(startMs = 10_000, endMs = 13_400)))
        assertEquals(emptyList(), Replays.fitTo(moved))
    }

    @Test
    fun `the replays keep the play order of the user`() {
        val replays = listOf(ReplayV1(startMs = 15_000, endMs = 17_000), ReplayV1(startMs = 11_000, endMs = 12_000))
        assertEquals(listOf(15_000, 11_000), Replays.fitTo(point.copy(replays = replays)).map { it.startMs })
    }

    @Test
    fun `the editor speeds are inside the limits and include the default`() {
        assertEquals(listOf(0.1, 0.2, 0.3, 0.5, 1.0), Replays.SPEEDS)
        assertTrue(Replays.SPEEDS.all { it in Replays.MIN_SPEED..Replays.MAX_SPEED })
        assertTrue(Replays.DEFAULT_SPEED in Replays.SPEEDS)
    }

    @Test
    fun `the speed goes into its limits`() {
        val fitted = Replays.fitTo(
            point.copy(
                replays = listOf(
                    ReplayV1(startMs = 11_000, endMs = 12_000, speed = 0.01),
                    ReplayV1(startMs = 12_000, endMs = 13_000, speed = 3.0),
                    ReplayV1(startMs = 13_000, endMs = 14_000, speed = Double.NaN),
                ),
            ),
        )
        assertEquals(listOf(0.1, 1.0, 0.5), fitted.map { it.speed })
    }

    @Test
    fun `a zoom frame stays inside the frame and is not smaller than 4x`() {
        assertEquals(ReplayZoom(0.75, 0.0, 0.25), Replays.normalizeZoom(ReplayZoom(0.9, -0.2, 0.25)))
        // A frame that is too small grows around its center.
        assertEquals(ReplayZoom(0.375, 0.375, 0.25), Replays.normalizeZoom(ReplayZoom(0.45, 0.45, 0.1)))
        assertNull(Replays.normalizeZoom(ReplayZoom(0.0, 0.0, 1.0)))
        assertNull(Replays.normalizeZoom(ReplayZoom(Double.NaN, 0.0, 0.5)))
    }

    @Test
    fun `a point without changes stays the same object`() {
        val withReplay = point.copy(replays = listOf(ReplayV1(startMs = 11_000, endMs = 12_000)))
        assertSame(withReplay, Replays.fitted(withReplay))
    }

    @Test
    fun `the added time is the part length divided by the speed`() {
        assertEquals(4_000L, Replays.addedMs(ReplayV1(startMs = 0, endMs = 2_000, speed = 0.5)))
    }

    @Test
    fun `the still frame adds its time and moves the later frames`() {
        val replay = ReplayV1(startMs = 10_000, endMs = 12_000, speed = 0.5, freeze = ReplayFreeze(11_000, 800))
        assertEquals(4_000L, Replays.motionMs(replay))
        assertEquals(4_800L, Replays.playMs(replay))
        assertEquals(4_800L, Replays.addedMs(replay))
        assertEquals(2_000L, Replays.freezeOffsetMs(replay))

        assertEquals(1_000L, Replays.playOffsetAt(replay, 10_500))
        assertEquals(2_000L, Replays.playOffsetAt(replay, 11_000))
        assertEquals(3_800L, Replays.playOffsetAt(replay, 11_500))

        assertEquals(10_500L, Replays.sourceAt(replay, 1_000))
        // During the still frame, the source time stays at the still frame.
        assertEquals(11_000L, Replays.sourceAt(replay, 2_500))
        assertEquals(11_500L, Replays.sourceAt(replay, 3_800))
        assertEquals(12_000L, Replays.sourceAt(replay, 4_800))
    }

    @Test
    fun `a replay has no still frame by default`() {
        val replay = ReplayV1(startMs = 10_000, endMs = 12_000, speed = 0.5)
        assertNull(replay.freeze)
        assertNull(Replays.freezeOffsetMs(replay))
        assertEquals(Replays.motionMs(replay), Replays.playMs(replay))
    }

    @Test
    fun `the still frame stays inside the part and its time in the limits`() {
        val fitted = Replays.fitTo(
            point.copy(
                replays = listOf(
                    ReplayV1(startMs = 11_000, endMs = 12_000, freeze = ReplayFreeze(15_000, 50)),
                    ReplayV1(startMs = 13_000, endMs = 14_000, freeze = ReplayFreeze(1_000, 60_000)),
                ),
            ),
        )
        assertEquals(
            listOf(ReplayFreeze(12_000, Replays.MIN_FREEZE_MS), ReplayFreeze(13_000, Replays.MAX_FREEZE_MS)),
            fitted.map { it.freeze },
        )
        assertEquals(Replays.DEFAULT_FREEZE_MS, ReplayFreeze(1_000).durationMs)
    }

    @Test
    fun `the timeline counts the still frame`() {
        val withFreeze = point.copy(replays = listOf(ReplayV1(startMs = 18_000, endMs = 20_000, speed = 0.5, freeze = ReplayFreeze(19_000, 800))))
        val play = org.litvin.points.ReplayTimeline(listOf(withFreeze)).inserts.single().plays.single()
        assertEquals(4_800L, play.durationMs)
        assertEquals(19_000L, play.sourceAt(play.startMs + 2_400))
        assertEquals(play.startMs + 3_800, play.playAt(19_500))
    }

    @Test
    fun `an old edl json with repeats reads and the replay plays once`() {
        val file = Files.createTempDirectory("edl_old_repeats_").resolve("edl.json").toFile()
        file.writeText(
            """
            {"version": 1, "points": [{"id": "P", "startMs": 10000, "endMs": 20000, "replays": [
              {"startMs": 12000, "endMs": 14000, "speed": 0.5, "repeats": 3}
            ]}]}
            """.trimIndent(),
        )
        val replay = EdlIO.read(file.absolutePath).points.single().replays.single()
        assertEquals(ReplayV1(startMs = 12_000, endMs = 14_000, speed = 0.5), replay)
        assertEquals(4_000L, Replays.addedMs(replay))
    }

    @Test
    fun `edl json keeps the replays`() {
        val path = Files.createTempDirectory("edl_replay_").resolve("edl.json").toFile().absolutePath
        val replay = ReplayV1(
            startMs = 12_000,
            endMs = 14_000,
            zoom = ReplayZoom(0.25, 0.3, 0.5),
            speed = 0.25,
            muted = true,
            effect = ReplayEffect.FLASH,
            showBadge = false,
            label = "Ace",
            freeze = ReplayFreeze(atMs = 13_000, durationMs = 1_500),
            comments = listOf(
                org.litvin.points.CommentV1(
                    id = 1, startMs = 12_500, durationMs = 2_000, text = "Watch the lob", colorHex = "#FFD400",
                    style = org.litvin.points.CommentStyle.PILL, position = org.litvin.points.CommentPosition(0.3, 0.4),
                ),
            ),
            showPointComments = false,
        )
        val edl = EdlV1(points = listOf(point.copy(replays = listOf(replay))))
        EdlIO.write(path, edl)
        assertEquals(edl, EdlIO.read(path))
    }

    @Test
    fun `a replay without comments writes no comments key and shows the point comments`() {
        val file = Files.createTempDirectory("edl_replay_no_comments_").resolve("edl.json").toFile()
        EdlIO.write(file.absolutePath, EdlV1(points = listOf(point.copy(replays = listOf(ReplayV1(12_000, 14_000))))))
        val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(file)
        assertFalse(json["points"][0]["replays"][0].has("comments"), file.readText())
        val replay = EdlIO.read(file.absolutePath).points.single().replays.single()
        assertEquals(emptyList(), replay.comments)
        assertTrue(replay.showPointComments)
    }

    private fun replayComment(id: Int, start: Int, duration: Int) =
        org.litvin.points.CommentV1(id = id, startMs = start, durationMs = duration, text = "C$id", colorHex = "#FFFFFF")

    @Test
    fun `a replay comment starts at the moment of its frame and shows for its time in the replay`() {
        // 2 s at half speed: the part plays in 4 s.
        val replay = ReplayV1(startMs = 10_000, endMs = 12_000, speed = 0.5, comments = listOf(replayComment(1, 11_000, 1_500)))
        val comment = replay.comments.single()
        assertEquals(2_000L, Replays.commentPlayStartMs(replay, comment))
        assertEquals(3_500L, Replays.commentPlayEndMs(replay, comment))
        assertEquals(11_750L, Replays.commentSourceEndMs(replay, comment))

        // At full speed, the comment starts at the same frame. The replay end at 2 s cuts it.
        val fast = replay.copy(speed = 1.0)
        assertEquals(1_000L, Replays.commentPlayStartMs(fast, comment))
        assertEquals(2_000L, Replays.commentPlayEndMs(fast, comment))

        assertEquals(emptyList(), Replays.commentsAt(replay, 1_999))
        assertEquals(listOf(comment), Replays.commentsAt(replay, 2_000))
        assertEquals(emptyList(), Replays.commentsAt(replay, 3_500))
        assertEquals(listOf(comment), Replays.commentsAtSource(replay, 11_500))
        assertEquals(emptyList(), Replays.commentsAtSource(replay, 12_500))
    }

    @Test
    fun `a replay comment on the still frame shows during the still frame and stops at the replay end`() {
        val replay = ReplayV1(
            startMs = 10_000, endMs = 12_000, speed = 0.5, freeze = ReplayFreeze(11_000, 800),
            comments = listOf(replayComment(1, 11_000, 800), replayComment(2, 11_800, 5_000)),
        )
        val (onFreeze, late) = replay.comments
        assertEquals(2_000L, Replays.commentPlayStartMs(replay, onFreeze))
        assertEquals(2_800L, Replays.commentPlayEndMs(replay, onFreeze))
        assertEquals(listOf(onFreeze), Replays.commentsAt(replay, 2_400))
        // The later comment comes after the still frame, and the replay end cuts it.
        assertEquals(4_400L, Replays.commentPlayStartMs(replay, late))
        assertEquals(4_800L, Replays.commentPlayEndMs(replay, late))
    }

    @Test
    fun `replay comments stay inside the part and their time in the limits`() {
        val fitted = Replays.fitTo(
            point.copy(
                replays = listOf(
                    ReplayV1(startMs = 11_000, endMs = 12_000, comments = listOf(replayComment(1, 15_000, 10), replayComment(2, 1_000, 90_000))),
                ),
            ),
        ).single()
        assertEquals(listOf(12_000, 11_000), fitted.comments.map { it.startMs })
        assertEquals(listOf(Replays.MIN_COMMENT_MS, Replays.MAX_COMMENT_MS), fitted.comments.map { it.durationMs })
        assertEquals(3, Replays.nextCommentId(fitted))
    }

    @Test
    fun `a point without replays writes no replays key`() {
        val file = Files.createTempDirectory("edl_no_replay_").resolve("edl.json").toFile()
        EdlIO.write(file.absolutePath, EdlV1(points = listOf(point)))
        assertFalse(file.readText().contains("replays"))
        assertEquals(emptyList(), EdlIO.read(file.absolutePath).points.single().replays)
    }

    @Test
    fun `read fits replays and reads an unknown effect as fade`() {
        val file = Files.createTempDirectory("edl_bad_replay_").resolve("edl.json").toFile()
        file.writeText(
            """
            {"version": 1, "points": [{"id": "P", "startMs": 10000, "endMs": 20000, "replays": [
              {"startMs": 1000, "endMs": 12000, "speed": 0.5, "effect": "SPIN"},
              {"startMs": 30000, "endMs": 31000}
            ]}]}
            """.trimIndent(),
        )
        val replays = EdlIO.read(file.absolutePath).points.single().replays
        assertEquals(listOf(ReplayV1(startMs = 7_000, endMs = 12_000, effect = ReplayEffect.FADE)), replays)
    }

    @Test
    fun `a point edit in the dispatcher fits the replays`() {
        val dispatcher = PointsDispatcher()
        dispatcher.setPoints(listOf(point.copy(replays = listOf(ReplayV1(startMs = 8_000, endMs = 12_000)))))
        assertTrue(dispatcher.updatePoint("P", 12_000, 20_000, null))
        assertEquals(
            listOf(ReplayV1(startMs = 9_000, endMs = 12_000)),
            dispatcher.getCompletedPoints().single().replays,
        )
    }

    @Test
    fun `delete removes the replays with the point`() {
        val dispatcher = PointsDispatcher()
        dispatcher.setPoints(listOf(point.copy(replays = listOf(ReplayV1(startMs = 11_000, endMs = 12_000)))))
        assertTrue(dispatcher.deletePoint("P"))
        assertTrue(dispatcher.getCompletedPoints().isEmpty())
    }

    @Test
    fun `only the first replay of a point zooms in`() {
        assertTrue(Replays.zoomsIn(replayIndex = 0))
        assertFalse(Replays.zoomsIn(replayIndex = 1))
    }

    @Test
    fun `the default label follows the place of the replay`() {
        val replay = ReplayV1(startMs = 9_000, endMs = 12_000)
        assertEquals("Replay", Replays.label(replay, 0))
        assertEquals("Replay 2", Replays.label(replay, 1))
        assertEquals("Ace", Replays.label(replay.copy(label = "Ace"), 1))
        assertEquals("Replay 3", Replays.label(replay.copy(label = "  "), 2))
    }

    @Test
    fun `the badge shows the label in capital letters`() {
        val replay = ReplayV1(startMs = 9_000, endMs = 12_000)
        assertEquals("REPLAY", Replays.badgeText(replay, 0))
        assertEquals("REPLAY 2", Replays.badgeText(replay, 1))
        assertEquals("GREAT LOB", Replays.badgeText(replay.copy(label = "Great lob"), 0))
    }

    @Test
    fun `a blank or default typed label is the default label`() {
        assertNull(Replays.labelOf("", 0))
        assertNull(Replays.labelOf("   ", 1))
        assertNull(Replays.labelOf("Replay", 0))
        assertNull(Replays.labelOf("Replay 2", 1))
        assertEquals("Replay", Replays.labelOf("Replay", 1))
        assertEquals("Ace", Replays.labelOf("  Ace ", 0))
        assertEquals(Replays.MAX_LABEL_LENGTH, Replays.labelOf("x".repeat(100), 0)?.length)
    }

    @Test
    fun `the export badge has the label of each replay`() {
        val withThree = point.copy(
            replays = listOf(
                ReplayV1(startMs = 9_000, endMs = 12_000),
                ReplayV1(startMs = 10_000, endMs = 12_000, label = "Great lob"),
                ReplayV1(startMs = 11_000, endMs = 13_000),
                ReplayV1(startMs = 11_000, endMs = 13_000, showBadge = false),
            ),
        )
        val replays = org.litvin.export.ExportReplays.of(listOf(withThree), emptyList())
        assertEquals(listOf("REPLAY", "GREAT LOB", "REPLAY 3", null), replays.map { it.badgeText })
    }

    @Test
    fun `the export replays know their place in the point`() {
        val withTwo = point.copy(replays = listOf(ReplayV1(startMs = 9_000, endMs = 12_000), ReplayV1(startMs = 10_000, endMs = 12_000)))
        val replays = org.litvin.export.ExportReplays.of(listOf(withTwo), emptyList())
        assertEquals(listOf(0, 1), replays.map { it.replayIndex })
    }
}
