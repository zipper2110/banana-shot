package org.litvin.export

import org.litvin.export.ExportPassPlanner.Card
import org.litvin.export.ExportPassPlanner.Video
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExportPassPlannerTest {
    // Each kept point is 5 s long.
    private val keeps = (1..6).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }

    private fun card(pages: Int) = StatsCardVideo(List(pages) { ScoreboardScene(1920.0, 1080.0, emptyList()) })

    private fun set(number: Int, vararg ids: String, pages: Int = 1) = SetSummaryCard(number, ids.toSet(), card(pages))

    private fun List<ExportPassPlanner.Pass>.describe(): List<String> = map { pass ->
        when (pass) {
            is Video -> "video ${pass.chunk.keeps.joinToString("+") { it.id }} @${pass.chunk.outputOffsetMs}/${pass.progressBaseMs}"
            is Card -> "card ${pass.card.durationMs} at ${pass.freezeAtMs} @${pass.progressBaseMs}"
            is ExportPassPlanner.Replay -> "replay ${pass.replay.pointId}#${pass.replay.replayIndex} ${pass.durationMs} @${pass.progressBaseMs}"
        }
    }

    @Test
    fun withoutCardsThePlanHasOnlyTheVideoChunks() {
        val passes = ExportPassPlanner.plan(keeps, maxSegmentsPerChunk = 4)

        assertEquals(listOf("video p1+p2+p3+p4 @0/0", "video p5+p6 @20000/20000"), passes.describe())
    }

    @Test
    fun aSetCardComesAfterTheLastKeptPointOfTheSet() {
        val passes = ExportPassPlanner.plan(
            keeps,
            setSummaries = listOf(set(1, "p1", "p2", "p3"), set(2, "p4", "p5", "p6", pages = 2)),
            endCard = card(1),
            maxSegmentsPerChunk = 8,
        )

        // The chunk offsets stay on the video timeline, for the scoreboard. The progress includes the cards.
        assertEquals(
            listOf(
                "video p1+p2+p3 @0/0",
                "card 6000 at 35000 @15000",
                "video p4+p5+p6 @15000/21000",
                "card 12000 at 65000 @36000",
                "card 6000 at 65000 @48000",
            ),
            passes.describe(),
        )
    }

    @Test
    fun aSetWithoutAKeptPointHasNoCard() {
        val favorites = listOf(keeps[3], keeps[4])

        val passes = ExportPassPlanner.plan(favorites, setSummaries = listOf(set(1, "p1", "p2", "p3"), set(2, "p4", "p5", "p6")))

        assertEquals(listOf("video p4+p5 @0/0", "card 6000 at 55000 @10000"), passes.describe())
    }

    @Test
    fun theChunkLimitAppliesInsideEachPartBetweenCards() {
        val passes = ExportPassPlanner.plan(keeps, setSummaries = listOf(set(1, "p1", "p2", "p3")), maxSegmentsPerChunk = 2)

        assertEquals(
            listOf(
                "video p1+p2 @0/0",
                "video p3 @10000/10000",
                "card 6000 at 35000 @15000",
                "video p4+p5 @15000/21000",
                "video p6 @25000/31000",
            ),
            passes.describe(),
        )
    }

    @Test
    fun aFullVideoHasOneVideoPassAndTheMatchCardAtTheEndOfTheSource() {
        val passes = ExportPassPlanner.plan(
            emptyList(),
            setSummaries = listOf(set(1, "p1")),
            endCard = card(2),
            fullVideoDurationMs = 90_000,
        )

        assertEquals(2, passes.size)
        assertIs<Video>(passes[0])
        val end = assertIs<Card>(passes[1])
        assertNull(end.freezeAtMs)
        assertEquals(90_000L, end.progressBaseMs)
        assertEquals(12_000L, ExportPassPlanner.cardDurationMs(passes))
    }

    // B-50: the replays.

    /** The replay [index] of point [id]: the last 2 s of the point at half speed, so the play is 4 s. */
    private fun replay(id: String, index: Int = 0, effect: ReplayEffect = ReplayEffect.FADE): ExportReplay {
        val point = keeps.first { it.id == id }
        val part = ReplayV1(point.endMs - 2_000, point.endMs, speed = 0.5, effect = effect)
        return ExportReplay(id, point.endMs, part, replayIndex = index)
    }

    private fun PassFade?.text() = when {
        this == null -> "-"
        white -> "white$durationMs"
        else -> "black$durationMs"
    }

    private fun List<ExportPassPlanner.Pass>.fades(): List<String> = map { pass ->
        when (pass) {
            is Video -> "${pass.fadeIn.text()}/${pass.fadeOut.text()}"
            is ExportPassPlanner.Replay -> "${pass.fadeIn.text()}/${pass.fadeOut.text()}"
            is Card -> "card"
        }
    }

    @Test
    fun theReplaysOfAPointComeAfterItsEndAndThePlaysAddToTheProgress() {
        val passes = ExportPassPlanner.plan(keeps, replays = listOf(replay("p2"), replay("p2", index = 1)), maxSegmentsPerChunk = 8)

        assertEquals(
            listOf(
                "video p1+p2 @0/0",
                "replay p2#0 4000 @10000",
                "replay p2#1 4000 @14000",
                "video p3+p4+p5+p6 @10000/18000",
            ),
            passes.describe(),
        )
        // Each replay plays once. The cut into and out of each replay dips to black.
        assertEquals(listOf("-/black250", "black250/black250", "black250/black250", "black250/-"), passes.fades())
        assertEquals(8_000L, ExportPassPlanner.extraDurationMs(passes))
    }

    @Test
    fun flashDipsToWhiteAndCutHasNoDip() {
        val flash = ExportPassPlanner.plan(keeps, replays = listOf(replay("p1", effect = ReplayEffect.FLASH)), maxSegmentsPerChunk = 8)
        assertEquals(listOf("-/white250", "white250/white250", "white250/-"), flash.fades())

        val cut = ExportPassPlanner.plan(keeps, replays = listOf(replay("p1", effect = ReplayEffect.CUT)), maxSegmentsPerChunk = 8)
        assertEquals(listOf("-/-", "-/-", "-/-"), cut.fades())
    }

    @Test
    fun theSetCardComesAfterTheReplaysOfTheLastPointOfTheSet() {
        val passes = ExportPassPlanner.plan(
            keeps,
            setSummaries = listOf(set(1, "p1", "p2", "p3")),
            replays = listOf(replay("p3")),
            maxSegmentsPerChunk = 8,
        )

        assertEquals(
            listOf(
                "video p1+p2+p3 @0/0",
                "replay p3#0 4000 @15000",
                "card 6000 at 35000 @19000",
                "video p4+p5+p6 @15000/25000",
            ),
            passes.describe(),
        )
        // A card between the replay and the video stops the fade in of the video.
        assertEquals(listOf("-/black250", "black250/black250", "card", "-/-"), passes.fades())
    }

    @Test
    fun aReplayOfAPointThatIsNotKeptIsNotInThePlan() {
        val passes = ExportPassPlanner.plan(keeps.take(2), replays = listOf(replay("p4")), maxSegmentsPerChunk = 8)

        assertEquals(listOf("video p1+p2 @0/0"), passes.describe())
    }

    @Test
    fun aFullVideoWithReplaysSplitsTheSourceAtThePointEnds() {
        val passes = ExportPassPlanner.plan(
            keeps = emptyList(),
            fullVideoDurationMs = 60_000,
            replays = listOf(replay("p1"), replay("p3")),
        )

        assertEquals(
            listOf(
                "video range-0 @0/0",
                "replay p1#0 4000 @15000",
                "video range-15000 @15000/19000",
                "replay p3#0 4000 @39000",
                "video range-35000 @35000/43000",
            ),
            passes.describe(),
        )
        val ranges = passes.filterIsInstance<Video>().map { it.chunk.keeps.single().let { k -> k.startMs to k.endMs } }
        assertEquals(listOf(0 to 15_000, 15_000 to 35_000, 35_000 to 60_000), ranges)
    }
}
