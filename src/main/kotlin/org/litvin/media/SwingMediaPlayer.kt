package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.geom.Rectangle2D
import java.io.File

interface SwingMediaPlayer : AutoCloseable {
    val component: Component
    var onReady: (() -> Unit)?
    var onStatusChanged: ((PlayerStatus) -> Unit)?
    var onTimeChanged: ((Long) -> Unit)?

    fun load(file: File)
    fun play()
    fun pause()
    fun seek(ms: Long)

    /**
     * Seeks fast for a drag on a timeline. The shown frame can be near [ms], not at [ms].
     * Call [seek] when the drag stops to show the exact frame.
     */
    fun scrub(ms: Long) = seek(ms)
    fun setRate(rate: Float)
    fun status(): PlayerStatus
    fun currentTimeMs(): Long
    fun totalDurationMs(): Long
    fun isAdjustSupported(): Boolean
    fun applyColorAdjustments(adj: AdjustmentsV1)
    fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean
    fun applyPreviewAdjustments(adj: AdjustmentsV1)
    fun applyPreviewRotation(rotationDeg: Float, reason: String = "apply rotation")
    /** Draws [overlay] over the video, for example the scoreboard. Null removes it. */
    fun setPreviewOverlay(overlay: VideoOverlay?)
    fun stepFrameForward(maximumTimeMs: Long = Long.MAX_VALUE): Long
    fun stepFrameBackward(minimumTimeMs: Long = 0L): Long
    fun nextFrame()
    fun setSubtitleFile(file: File): Boolean
    fun activatePreview(reason: String = "activate")
    fun deactivatePreview(reason: String = "deactivate")
    override fun close()

    /**
     * True shows the rotated full frame without the crop, for the Crop/Rotate editor.
     * False shows the export result (rotation and crop). Engines without this feature ignore it.
     */
    fun setCropEditing(enabled: Boolean) {}

    /** The size of the loaded video in source pixels, or null when it is not known. */
    fun videoSize(): Dimension? = null

    /** The area of [component] that shows the video, in component pixels. Null when it is not known. */
    fun videoBounds(): Rectangle2D.Double? = null

    /**
     * The number of overlay pixels in one component pixel. A [VideoOverlay] gets its video area in overlay pixels.
     * With display scaling above 100%, the overlay uses device pixels, so the value is above 1.
     */
    fun overlayScale(): Double = 1.0

    /** Called on the event dispatch thread when [videoBounds] changes. */
    var onVideoBoundsChanged: (() -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /**
     * Draws the shapes over the video. The coordinates are component pixels. Null removes the shapes.
     * Engines without this feature ignore it.
     */
    fun setEditorOverlay(shapes: List<OverlayShape>?) {}

    /**
     * Shows the picture of a replay (B-50): the zoom frame and the dip to black or white. Null shows the normal
     * picture. Engines without this feature ignore it.
     */
    fun setReplayView(view: ReplayView?) {}

    /**
     * Plays the sound and the video of a replay (B-50): slower, with a lower pitch, or muted. Null plays them
     * normally. The speed multiplies the speed of [setRate]. Engines without this feature ignore it.
     */
    fun setReplaySound(sound: ReplaySound?) {}

    /** Called on the player thread when the playback starts again after a seek. */
    var onPlaybackRestart: (() -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
}

/**
 * The picture of a replay. The zoom frame ([zoomX], [zoomY], [zoomWidth]) is a part of the frame after the
 * Transform tab, as fractions of that frame. It has the aspect of the frame. [dip] is the amount of the dip:
 * 0 shows the picture, and 1 shows only black (or white when [dipWhite]).
 */
data class ReplayView(
    val zoomX: Double = 0.0,
    val zoomY: Double = 0.0,
    val zoomWidth: Double = 1.0,
    val dip: Double = 0.0,
    val dipWhite: Boolean = false,
) {
    val isNormal: Boolean get() = zoomWidth >= 1.0 && dip <= 0.0
}

/** The sound of a replay: the speed of the part (below 1 is slower), and if the sound is off. */
data class ReplaySound(val speed: Double, val muted: Boolean)

/**
 * ASS events that a player draws over the video with libass. The export burns the same events
 * into the video, so the preview and the export look the same.
 */
fun interface VideoOverlay {
    /**
     * Returns the ASS event texts (the Text field of Dialogue lines) in drawing order.
     * [videoArea] is the area that shows the video, in overlay pixels. The events use the same pixels.
     */
    fun assEvents(videoArea: Rectangle2D.Double): List<String>
}

/** A vector shape that a player draws over the video. The coordinates are component pixels. */
sealed interface OverlayShape {
    /**
     * A rectangle. A [radius] above 0 gives it round corners.
     * [rotationDeg] turns it around its center. A positive angle turns it clockwise.
     */
    data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val fill: Color? = null,
        val stroke: Color? = null,
        val strokeWidth: Double = 0.0,
        val radius: Double = 0.0,
        val rotationDeg: Double = 0.0,
    ) : OverlayShape

    /**
     * One line of text. ([x], [y]) is the top-left corner of the line box.
     * [size] is the height of the line box (ascent + descent), as libass uses it.
     */
    data class Text(
        val x: Double,
        val y: Double,
        val text: String,
        val size: Double,
        val color: Color,
        val font: String,
        val bold: Boolean = false,
    ) : OverlayShape

    data class Circle(
        val centerX: Double,
        val centerY: Double,
        val radius: Double,
        val fill: Color? = null,
        val stroke: Color? = null,
        val strokeWidth: Double = 0.0,
    ) : OverlayShape

    data class Line(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val color: Color,
        val width: Double,
    ) : OverlayShape
}
