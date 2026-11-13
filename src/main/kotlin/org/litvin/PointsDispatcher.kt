package org.litvin.points.components

import org.litvin.i18n.tr
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.points.EdlIO
import org.litvin.points.PointV1
import org.litvin.points.Replays
import org.litvin.shared.util.Timecode

/**
 * Dispatcher and simple state holder for Points tab (task 2.4: auto-create point from Start/End).
 * - Maintains an in-memory list of created points (completed intervals)
 * - Maintains a single pending Start until End is set or replaced
 * - Rounds incoming timestamps to nearest 10 ms
 * - Validates duration >= 200 ms and start < end on finalize
 * - Keeps a fault clip of zero length (B-68): X with no open point adds it at the playhead. Only such a clip has zero
 *   length. The list is in time order: the start, then the end.
 */
class PointsDispatcher {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    data class UiRow(val title: String, val startMs: Int?, val endMs: Int?, val isPending: Boolean)

    private val points = mutableListOf<PointV1>()
    private var pendingStartMs: Int? = null
    private var pendingId: String? = null
    private var userMessage: String? = null

    /** Callback invoked whenever completed points collection changes (create/delete/replace/clear). */
    var onPointsChanged: (() -> Unit)? = null

    private fun notifyUser(msg: String) {
        userMessage = msg
        logger.info { "User hint: $msg" }
    }

    fun consumeUserMessage(): String? {
        val m = userMessage
        userMessage = null
        return m
    }

    /** Update an existing point by id with validation (2.14/2.7). Returns true on success. */
    fun updatePoint(id: String, newStartMsRaw: Long, newEndMsRaw: Long, newLabel: String?): Boolean {
        val idx = points.indexOfFirst { it.id == id }
        if (idx < 0) {
            notifyUser(tr("dispatch.point.notFound", "id" to id))
            return false
        }
        val s = Timecode.roundTo10ms(newStartMsRaw)
        val e = Timecode.roundTo10ms(newEndMsRaw)
        // A clip of zero length can keep zero length (B-68), for example when the Edit dialog changes only its outcome.
        val keepsZeroLength = e == s && points[idx].endMs <= points[idx].startMs
        if (s < 0 || e < 0 || (!keepsZeroLength && (e <= s || (e - s) < 200))) {
            notifyUser(tr("dispatch.point.invalidEdit"))
            return false
        }
        // Overlap check against all other points (half-open [s,e))
        val overlaps = points.withIndex().any { (i, p) ->
            if (i == idx) return@any false
            s < p.endMs && p.startMs < e
        }
        if (overlaps) {
            notifyUser(tr("dispatch.point.editOverlap"))
            return false
        }
        val old = points[idx]
        // The replays keep their source times and stay inside the new window of the point (B-50).
        val updated = Replays.fitted(old.copy(startMs = s, endMs = e, label = newLabel))
        points[idx] = updated
        sortPoints()
        onPointsChanged?.invoke()
        return true
    }


    /** Called when user presses C or clicks Point Start. */
    fun onPointStart(timeMs: Long) {
        val t = Timecode.roundTo10ms(timeMs)
        // Block placing a Start inside an existing interval [start, end)
        val insideExisting = points.any { p -> t >= p.startMs && t < p.endMs }
        if (insideExisting) {
            notifyUser(tr("dispatch.point.startInside"))
            return
        }
        // Assign a stable id at the moment pending row is created (2.16). If pending exists, keep the same id.
        if (pendingStartMs == null) {
            pendingId = EdlIO.generateId()
        }
        pendingStartMs = t
        logger.debug { "Pending Start set at $pendingStartMs ms (id=${pendingId})" }
        // Notify UI so it can render/update the pending row immediately
        onPointsChanged?.invoke()
    }

    /** Half-open overlap check against existing points: [s, e) intersects [p.start, p.end) */
    private fun overlapsExisting(newStart: Int, newEnd: Int): Boolean {
        return points.any { p ->
            val ps = p.startMs
            val pe = p.endMs
            // Overlap if starts before existing end and ends after existing start
            newStart < pe && ps < newEnd
        }
    }

    /**
     * Ends the pending point at [timeMs]. Returns true when a point was created.
     * The Points tab calls it only for Q, W and E, and then gives the outcome to the new point at once.
     */
    fun onPointEnd(timeMs: Long): Boolean {
        val s = pendingStartMs
        if (s == null) {
            // No-op per spec (gentle hint)
            notifyUser(tr("dispatch.point.noStart"))
            return false
        }
        val e = Timecode.roundTo10ms(timeMs)
        val duration = e - s
        if (e <= s || duration < 200) {
            // Invalid; keep pending and show hint
            notifyUser(tr("dispatch.point.invalid"))
            return false
        }
        // Validation 2.7: reject overlaps with existing points; adjacent allowed via half-open rule
        if (overlapsExisting(s, e)) {
            notifyUser(tr("dispatch.point.overlap"))
            return false
        }
        val id = pendingId ?: EdlIO.generateId()
        points.add(PointV1(id = id, startMs = s, endMs = e))
        // Keep list sorted by start time to satisfy 2.4/2.6 acceptance
        sortPoints()
        // Clear pending after successful creation
        pendingStartMs = null
        pendingId = null
        logger.debug { "Point created: $id [$s, $e]" }
        onPointsChanged?.invoke()
        return true
    }

    /**
     * Adds a fault clip of zero length at [timeMs] (B-68): X with no open point and no active clip. Returns the id of
     * the new clip, or null when [timeMs] is inside a clip or a clip of zero length is already at [timeMs]. The
     * scoring then gives the clip the ending SERVE_FAULT.
     */
    fun addZeroLengthPoint(timeMs: Long): String? {
        val t = Timecode.roundTo10ms(timeMs)
        if (t < 0) return null
        if (points.any { p -> (t >= p.startMs && t < p.endMs) || (p.startMs == t && p.endMs == t) }) {
            notifyUser(tr("dispatch.point.startInside"))
            return null
        }
        val id = EdlIO.generateId()
        points.add(PointV1(id = id, startMs = t, endMs = t))
        sortPoints()
        logger.debug { "Fault clip of zero length created: $id [$t]" }
        onPointsChanged?.invoke()
        return id
    }

    /** The time order: the start, then the end. Thus a clip of zero length comes before a clip that starts at the same time. */
    private fun sortPoints() = points.sortWith(compareBy({ it.startMs }, { it.endMs }))

    fun getCompletedPoints(): List<PointV1> = points.toList()

    fun setPoints(newPoints: List<PointV1>) {
        points.clear()
        points.addAll(newPoints.sortedWith(compareBy({ it.startMs }, { it.endMs })))
        onPointsChanged?.invoke()
    }

    /** Replaces the replays of the point with [id] (B-50). The replays are fitted to the point. Returns true if updated. */
    fun setReplays(id: String, replays: List<org.litvin.points.ReplayV1>): Boolean {
        val idx = points.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val updated = Replays.fitted(points[idx].copy(replays = replays))
        if (updated == points[idx]) return true
        points[idx] = updated
        onPointsChanged?.invoke()
        return true
    }

    /** Toggle favorite status for a completed point by id. Returns true if updated. */
    fun toggleFavorite(id: String): Boolean {
        val idx = points.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val p = points[idx]
        points[idx] = p.copy(favorite = !p.favorite)
        onPointsChanged?.invoke()
        return true
    }

    fun getPendingStart(): Int? = pendingStartMs

    /** The id that the pending point gets when it ends, or null when no point is pending. Marks can use it before (B-68). */
    fun getPendingId(): String? = pendingId

    /** Delete a point by id. Returns true if removed. */
    fun deletePoint(id: String): Boolean {
        val idx = points.indexOfFirst { it.id == id }
        if (idx >= 0) {
            points.removeAt(idx)
            onPointsChanged?.invoke()
            return true
        }
        return false
    }

    fun clearAll() {
        points.clear()
        pendingStartMs = null
        pendingId = null
        onPointsChanged?.invoke()
    }

    /** Clears the pending Start marker without affecting existing points. */
    fun clearPending() {
        if (pendingStartMs != null) {
            pendingStartMs = null
            pendingId = null
            onPointsChanged?.invoke()
        }
    }

    // Transport telemetry (optional logging)
    fun onPlayPauseClicked() { logger.debug { "Play/Pause clicked" } }
    fun onJumpBackClicked() { logger.debug { "Jump Back clicked" } }
    fun onJumpForwardClicked() { logger.debug { "Jump Forward clicked" } }
}
