package org.litvin.ui.tabs.export

import org.litvin.export.ExportReplay
import org.litvin.export.preview.ExportPreviewTimeline
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.i18n.tr
import org.litvin.points.ReplayV1
import org.litvin.stats.StatsCardVideo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers the names of the parts in the strip of the export preview (B-75 T5). */
class ExportPreviewStripTest {
    private val card = StatsCardVideo(listOf(ScoreboardScene(100.0, 100.0, emptyList())), pageDurationMs = 1_000L)
    private val point = ExportPreviewTimeline.Video(0L, 10_000L, 14_000L, 0L, "p1")
    private val video = ExportPreviewTimeline.Video(0L, 0L, 60_000L, 0L, null)
    private val replay = ExportPreviewTimeline.Replay(4_000L, ExportReplay("p1", 14_000, ReplayV1(12_000, 14_000)))
    private val otherReplay = ExportPreviewTimeline.Replay(4_000L, ExportReplay("gone", 14_000, ReplayV1(12_000, 14_000)))
    private val setCard = ExportPreviewTimeline.Card(8_000L, card, 14_000L, 2)
    private val matchCard = ExportPreviewTimeline.Card(9_000L, card, null, null)

    private val strip = ExportPreviewStrip(
        ExportPreviewTimeline(listOf(point, replay, setCard, matchCard)),
        pointNumbers = mapOf("p1" to 7),
        onSelect = {},
    )

    @Test
    fun eachPartHasTheNameOfItsKind() {
        assertEquals(tr("export.preview.item.point", "number" to 7), strip.describe(point))
        assertEquals(tr("export.preview.item.video"), strip.describe(video))
        assertEquals(tr("export.preview.item.replay", "number" to 7), strip.describe(replay))
        assertEquals(tr("export.preview.item.replayOnly"), strip.describe(otherReplay))
        assertEquals(tr("export.preview.item.setCard", "set" to 2), strip.describe(setCard))
        assertEquals(tr("export.preview.item.matchCard"), strip.describe(matchCard))
    }
}
