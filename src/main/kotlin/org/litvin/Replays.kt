package org.litvin.points

/**
 * The limits of the instant replays of a point (B-50), and the rules that keep a replay inside them.
 * See `docs/backlog.md` (B-50) and `docs/replay/b-50-tasks.md`.
 */
object Replays {
    /** A replay part can start this much before the point start and end this much after the point end. */
    const val MARGIN_MS = 3_000

    /** A shorter part is removed. */
    const val MIN_PART_MS = 500

    const val MIN_SPEED = 0.1
    const val MAX_SPEED = 1.0
    const val DEFAULT_SPEED = 0.5

    const val MIN_REPEATS = 1
    const val MAX_REPEATS = 5

    /** The narrowest zoom frame, a zoom of 4×. */
    const val MIN_ZOOM_WIDTH = 0.25

    /** A zoom frame this wide or wider is no zoom. */
    private const val NO_ZOOM_WIDTH = 0.999

    /** A new replay starts this much before the playhead and ends this much after it (decision of 2026-10-26). */
    const val NEW_PART_LEAD_MS = 1_500

    /** The earliest start and the latest end of a replay part of [point]. */
    fun windowStartMs(point: PointV1): Int = (point.startMs - MARGIN_MS).coerceAtLeast(0)

    fun windowEndMs(point: PointV1): Int = point.endMs + MARGIN_MS

    /**
     * A new replay of [point] around [playheadMs], with the default values. The part keeps its length of 3 s
     * when the playhead is near the edge of the window. Returns null when the window has no room for a part.
     */
    fun newAt(point: PointV1, playheadMs: Long): ReplayV1? {
        val windowStart = windowStartMs(point)
        val windowEnd = windowEndMs(point)
        val length = NEW_PART_LEAD_MS * 2
        val latestStart = (windowEnd - length).coerceAtLeast(windowStart)
        val start = (playheadMs - NEW_PART_LEAD_MS).coerceIn(windowStart.toLong(), latestStart.toLong()).toInt()
        val end = (start + length).coerceAtMost(windowEnd)
        if (end - start < MIN_PART_MS) return null
        return ReplayV1(startMs = start, endMs = end)
    }

    /**
     * The replays of [point] after a change of the point (decision 6): each part is cut to the window of the point,
     * a part shorter than [MIN_PART_MS] is removed, and the values go into their limits. The result is sorted by
     * start time (decision of 2026-10-26).
     */
    fun fitTo(point: PointV1): List<ReplayV1> {
        if (point.replays.isEmpty()) return point.replays
        val windowStart = windowStartMs(point)
        val windowEnd = windowEndMs(point)
        return point.replays
            .mapNotNull { replay ->
                val start = replay.startMs.coerceAtLeast(windowStart)
                val end = replay.endMs.coerceAtMost(windowEnd)
                if (end - start < MIN_PART_MS) null else normalizeValues(replay.copy(startMs = start, endMs = end))
            }
            .sortedWith(compareBy<ReplayV1> { it.startMs }.thenBy { it.endMs })
    }

    /** [point] with its replays fitted to it. The same object when nothing changes. */
    fun fitted(point: PointV1): PointV1 {
        val replays = fitTo(point)
        return if (replays == point.replays) point else point.copy(replays = replays)
    }

    /** Puts the speed, the repeat count, and the zoom frame into their limits. */
    internal fun normalizeValues(replay: ReplayV1): ReplayV1 {
        val speed = if (replay.speed.isFinite()) replay.speed.coerceIn(MIN_SPEED, MAX_SPEED) else DEFAULT_SPEED
        val repeats = replay.repeats.coerceIn(MIN_REPEATS, MAX_REPEATS)
        val zoom = normalizeZoom(replay.zoom)
        return if (speed == replay.speed && repeats == replay.repeats && zoom == replay.zoom) {
            replay
        } else {
            replay.copy(speed = speed, repeats = repeats, zoom = zoom)
        }
    }

    /**
     * A zoom frame inside the frame. A frame that is too small gets [MIN_ZOOM_WIDTH] around its center.
     * A frame that is not a number, or that is as wide as the frame, gives null (no zoom).
     */
    internal fun normalizeZoom(zoom: ReplayZoom?): ReplayZoom? {
        if (zoom == null || !zoom.x.isFinite() || !zoom.y.isFinite() || !zoom.width.isFinite()) return null
        if (zoom.width >= NO_ZOOM_WIDTH) return null
        val width = zoom.width.coerceAtLeast(MIN_ZOOM_WIDTH)
        val grow = (width - zoom.width) / 2
        val x = (zoom.x - grow).coerceIn(0.0, 1.0 - width)
        val y = (zoom.y - grow).coerceIn(0.0, 1.0 - width)
        val result = ReplayZoom(x, y, width)
        return if (result == zoom) zoom else result
    }

    /** The time that the replay adds to the video: the part length divided by the speed, for each repeat. */
    fun addedMs(replay: ReplayV1): Long =
        Math.round((replay.endMs - replay.startMs).coerceAtLeast(0) / replay.speed) * replay.repeats
}
