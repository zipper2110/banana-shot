package org.litvin.ui.tabs.points.replay

import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.ReplayZoom
import org.litvin.points.Replays

/**
 * The replays of one point while the replay editor is open (B-50 T4). The editor changes a copy, so Cancel keeps
 * the saved replays. Each change keeps the selected replay inside the limits of [Replays].
 */
class ReplayEditorModel(val point: PointV1, selectedIndex: Int = 0) {
    private val list: MutableList<ReplayV1> = Replays.fitTo(point).toMutableList()

    /** The replays in the editor, in the order of their tabs. */
    val replays: List<ReplayV1> get() = list.toList()

    /** The index of the selected replay, or -1 when the point has no replay. */
    var selectedIndex: Int = if (list.isEmpty()) -1 else selectedIndex.coerceIn(0, list.lastIndex)
        private set

    val selected: ReplayV1? get() = list.getOrNull(selectedIndex)

    /** True when the replays differ from the saved replays of the point. */
    val changed: Boolean get() = list != Replays.fitTo(point)

    val windowStartMs: Int get() = Replays.windowStartMs(point)
    val windowEndMs: Int get() = Replays.windowEndMs(point)

    fun select(index: Int) {
        if (index in list.indices) selectedIndex = index
    }

    /** Adds a replay around [playheadMs] and selects it. Returns false when the window has no room for it. */
    fun add(playheadMs: Long): Boolean {
        val replay = Replays.newAt(point, playheadMs) ?: return false
        list += replay
        selectedIndex = list.lastIndex
        return true
    }

    /** Removes the selected replay. The replay before it is selected next. */
    fun deleteSelected() {
        if (selectedIndex !in list.indices) return
        list.removeAt(selectedIndex)
        selectedIndex = if (list.isEmpty()) -1 else (selectedIndex - 1).coerceAtLeast(0)
    }

    /** Moves the start of the part to [ms]. The part stays at least [Replays.MIN_PART_MS] long. */
    fun setStart(ms: Long) = update { replay ->
        val start = ms.coerceIn(windowStartMs.toLong(), (replay.endMs - Replays.MIN_PART_MS).toLong()).toInt()
        replay.copy(startMs = start)
    }

    /** Moves the end of the part to [ms]. The part stays at least [Replays.MIN_PART_MS] long. */
    fun setEnd(ms: Long) = update { replay ->
        val end = ms.coerceIn((replay.startMs + Replays.MIN_PART_MS).toLong(), windowEndMs.toLong()).toInt()
        replay.copy(endMs = end)
    }

    /** The part is the whole point. */
    fun setWhole() = update { it.copy(startMs = point.startMs, endMs = point.endMs) }

    fun setSpeed(speed: Double) = update { it.copy(speed = speed) }

    fun setRepeats(repeats: Int) = update { it.copy(repeats = repeats) }

    fun setMuted(muted: Boolean) = update { it.copy(muted = muted) }

    fun setEffect(effect: ReplayEffect) = update { it.copy(effect = effect) }

    fun setShowBadge(show: Boolean) = update { it.copy(showBadge = show) }

    /** Sets the zoom frame. Null or the full frame is no zoom. */
    fun setZoom(zoom: ReplayZoom?) = update { it.copy(zoom = zoom) }

    /**
     * Sets a zoom of [factor] (1.5 is 1.5×) around the center of the current frame, or around the frame center.
     * A factor of 1 or less is no zoom.
     */
    fun setZoomFactor(factor: Double) = update { replay ->
        if (factor <= 1.0) return@update replay.copy(zoom = null)
        val width = 1.0 / factor
        val old = replay.zoom
        val centerX = old?.let { it.x + it.width / 2 } ?: 0.5
        val centerY = old?.let { it.y + it.width / 2 } ?: 0.5
        replay.copy(zoom = ReplayZoom(centerX - width / 2, centerY - width / 2, width))
    }

    /** The zoom of the selected replay as a factor, for example 2.0 for 2×. 1.0 is no zoom. */
    val zoomFactor: Double get() = selected?.zoom?.let { 1.0 / it.width } ?: 1.0

    /** The time that all replays of the point add to the video. */
    val addedMs: Long get() = list.sumOf { Replays.addedMs(it) }

    private fun update(change: (ReplayV1) -> ReplayV1) {
        val old = selected ?: return
        list[selectedIndex] = Replays.normalizeValues(change(old))
    }
}
