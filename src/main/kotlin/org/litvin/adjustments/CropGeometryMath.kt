package org.litvin.adjustments

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class CropRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

object CropGeometryMath {
    private const val MIN_OVERLAY_SIZE = 24.0
    private const val EPSILON = 0.000001

    /** The zoom tolerance of [keepCornersOut]: a zoom this near the auto-fit zoom is at the auto-fit zoom. */
    private const val FIT_TOLERANCE = 0.002f

    /**
     * The crop rectangle of the model values, in the pixels of a [outputWidth] x [outputHeight] frame.
     * The size comes from the zoom: 100% is the full frame size, also with a rotation. Then the corners of the
     * rotated frame show the fill color. [keepCornersOut] raises the zoom when the angle changes.
     * The pan moves the rectangle inside the bounding box of the rotated frame ([rotatedBounds]): -1 and 1 put it at
     * the edges of that box. So the rectangle can go past the frame edges and show the fill color.
     * Values of an older version use the older rules ([legacyOverlay]).
     */
    fun overlayFromModel(outputWidth: Double, outputHeight: Double, adjustments: AdjustmentsV1): CropRect {
        if (outputWidth <= 0.0 || outputHeight <= 0.0) return CropRect(0.0, 0.0, 0.0, 0.0)
        if (adjustments.version < AdjustmentsV1.CURRENT_VERSION) return legacyOverlay(outputWidth, outputHeight, adjustments)

        val rotation = adjustments.rotationDeg.toDouble()
        val aspect = outputWidth / outputHeight
        val zoom = adjustments.zoom.coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM).toDouble()
        val cropWidth = cropWidthFor(outputWidth, outputWidth / zoom)
        val cropHeight = cropWidth / aspect
        val canvas = rotatedBounds(CropRect(0.0, 0.0, outputWidth, outputHeight), rotation)
        val travelX = (canvas.width - cropWidth).coerceAtLeast(0.0)
        val travelY = (canvas.height - cropHeight).coerceAtLeast(0.0)
        val centerX = outputWidth / 2.0 + adjustments.panX.coerceIn(-1.0f, 1.0f) * travelX / 2.0
        val centerY = outputHeight / 2.0 - adjustments.panY.coerceIn(-1.0f, 1.0f) * travelY / 2.0

        return clampInside(
            CropRect(centerX - cropWidth / 2.0, centerY - cropHeight / 2.0, cropWidth, cropHeight),
            canvas,
            aspect,
        )
    }

    /** The model values of a crop rectangle in frame pixels. The inverse of [overlayFromModel] for the current version. */
    fun modelFromOverlay(
        outputWidth: Double,
        outputHeight: Double,
        overlay: CropRect,
        previous: AdjustmentsV1,
    ): AdjustmentsV1 {
        if (outputWidth <= 0.0 || outputHeight <= 0.0 || overlay.width <= 0.0 || overlay.height <= 0.0) {
            return previous
        }

        val rotation = previous.rotationDeg.toDouble()
        val aspect = outputWidth / outputHeight
        val frame = CropRect(0.0, 0.0, outputWidth, outputHeight)
        val clamped = fitToRotatedFrame(overlay, frame, rotation, aspect)
        val zoom = (outputWidth / clamped.width).toFloat().coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM)
        // The size that overlayFromModel gets back from this zoom, so that the pan gives the same position.
        val cropWidth = cropWidthFor(outputWidth, outputWidth / zoom)
        val cropHeight = cropWidth / aspect
        val canvas = rotatedBounds(frame, rotation)
        val travelX = canvas.width - cropWidth
        val travelY = canvas.height - cropHeight
        val centerX = clamped.x + clamped.width / 2.0
        val centerY = clamped.y + clamped.height / 2.0
        val panX = if (travelX > 0.5) {
            ((2.0 * centerX - outputWidth) / travelX).coerceIn(-1.0, 1.0).toFloat()
        } else {
            0.0f
        }
        val panY = if (travelY > 0.5) {
            ((outputHeight - 2.0 * centerY) / travelY).coerceIn(-1.0, 1.0).toFloat()
        } else {
            0.0f
        }

        return previous.copy(zoom = zoom, panX = panX, panY = panY)
    }

    /** The crop width for a [wanted] width: not smaller than the minimum, and not larger than the frame. */
    private fun cropWidthFor(width: Double, wanted: Double): Double =
        wanted.coerceIn(MIN_OVERLAY_SIZE.coerceAtMost(width), width)

    /**
     * The auto-fit zoom of an angle: the smallest zoom that shows no corners of the rotated frame
     * when the crop rectangle is at the center. It is 1 without rotation.
     */
    fun fitZoom(width: Double, height: Double, rotationDeg: Double): Float {
        if (width <= 0.0 || height <= 0.0) return AdjustmentsV1.MIN_ZOOM
        val inscribed = largestCenteredInscribedRect(width, height, rotationDeg, width / height)
        return (width / inscribed.width).toFloat().coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM)
    }

    /**
     * The zoom of [next] after an angle change from [previous], for a [width] x [height] frame.
     * When the zoom of [previous] was the auto-fit zoom of its angle, the zoom follows the auto-fit zoom of the new
     * angle, so a rotation shows no black corners and a rotation back to 0° gives 100% again. When the zoom was
     * larger, it becomes at least the new auto-fit zoom. When the zoom was smaller, the user chose to show the corners,
     * and the zoom does not change.
     */
    fun keepCornersOut(previous: AdjustmentsV1, next: AdjustmentsV1, width: Double, height: Double): AdjustmentsV1 {
        if (width <= 0.0 || height <= 0.0) return next
        if (abs(next.rotationDeg - previous.rotationDeg) < 0.0001f) return next
        val previousFit = fitZoom(width, height, previous.rotationDeg.toDouble())
        val nextFit = fitZoom(width, height, next.rotationDeg.toDouble())
        val zoom = when {
            abs(previous.zoom - previousFit) <= FIT_TOLERANCE -> nextFit
            previous.zoom > previousFit -> max(next.zoom, nextFit)
            else -> next.zoom
        }
        return next.copy(zoom = zoom)
    }

    /**
     * Converts the geometry of an older version (the base values and each change point) to the current version,
     * for a [width] x [height] frame. Each crop rectangle stays the same. Values of the current version do not change.
     */
    fun withCurrentVersion(adjustments: AdjustmentsV1, width: Double, height: Double): AdjustmentsV1 {
        if (adjustments.version >= AdjustmentsV1.CURRENT_VERSION || width <= 0.0 || height <= 0.0) return adjustments
        fun convert(zoom: Float, panX: Float, panY: Float, rotationDeg: Float): AdjustmentsV1 {
            val values = AdjustmentsV1(zoom = zoom, panX = panX, panY = panY, rotationDeg = rotationDeg)
            return modelFromOverlay(width, height, legacyOverlay(width, height, values), values)
        }
        val base = convert(adjustments.zoom, adjustments.panX, adjustments.panY, adjustments.rotationDeg)
        val keys = adjustments.geometryKeys.map { key ->
            val values = convert(key.zoom, key.panX, key.panY, key.rotationDeg)
            key.copy(zoom = values.zoom, panX = values.panX, panY = values.panY)
        }
        return adjustments.copy(
            zoom = base.zoom,
            panX = base.panX,
            panY = base.panY,
            geometryKeys = keys,
            version = AdjustmentsV1.CURRENT_VERSION,
        )
    }

    /**
     * The crop rectangle of version 1: the pan moves the rectangle inside the frame, and the size is not larger than
     * the largest centered rectangle inside the rotated frame. The export of older projects stays the same until
     * [withCurrentVersion] converts them.
     */
    private fun legacyOverlay(width: Double, height: Double, adjustments: AdjustmentsV1): CropRect {
        val aspect = width / height
        val zoom = adjustments.zoom.coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM).toDouble()
        val cropWidth = (width / zoom).coerceIn(MIN_OVERLAY_SIZE.coerceAtMost(width), width)
        val cropHeight = (height / zoom).coerceIn(MIN_OVERLAY_SIZE.coerceAtMost(height), height)
        val travelX = (width - cropWidth).coerceAtLeast(0.0)
        val travelY = (height - cropHeight).coerceAtLeast(0.0)
        val centerX = width / 2.0 + adjustments.panX.coerceIn(-1.0f, 1.0f) * travelX / 2.0
        val centerY = height / 2.0 - adjustments.panY.coerceIn(-1.0f, 1.0f) * travelY / 2.0
        val model = CropRect(
            x = (centerX - cropWidth / 2.0).coerceIn(0.0, travelX),
            y = (centerY - cropHeight / 2.0).coerceIn(0.0, travelY),
            width = cropWidth,
            height = cropHeight,
        )
        val maxWidth = largestCenteredInscribedRect(width, height, adjustments.rotationDeg.toDouble(), aspect).width
        return clampInside(shrinkAboutCenter(model, maxWidth, aspect), CropRect(0.0, 0.0, width, height), aspect)
    }

    /** [rect] with a width of at most [maxWidth]. A larger [rect] shrinks about its center. */
    private fun shrinkAboutCenter(rect: CropRect, maxWidth: Double, aspect: Double): CropRect {
        if (rect.width <= maxWidth) return rect
        val height = maxWidth / aspect
        return CropRect(
            x = rect.x + (rect.width - maxWidth) / 2.0,
            y = rect.y + (rect.height - height) / 2.0,
            width = maxWidth,
            height = height,
        )
    }

    /**
     * The bounding box of [frame] when it turns by [rotationDeg] about its center. The box has the same center.
     * Without rotation it is [frame].
     */
    fun rotatedBounds(frame: CropRect, rotationDeg: Double): CropRect {
        val radians = Math.toRadians(normalizeRotation(rotationDeg.toFloat()).toDouble())
        val cos = abs(cos(radians))
        val sin = abs(sin(radians))
        // Round away the error of the trigonometry, so that 0° and 180° give the frame exactly.
        fun clean(value: Double) = Math.round(value * 1e9) / 1e9
        val width = max(frame.width, clean(frame.width * cos + frame.height * sin))
        val height = max(frame.height, clean(frame.width * sin + frame.height * cos))
        return CropRect(
            x = frame.x - (width - frame.width) / 2.0,
            y = frame.y - (height - frame.height) / 2.0,
            width = width,
            height = height,
        )
    }

    /**
     * The scale of the area that the Transform editor shows, relative to the frame. The area has the frame aspect
     * and the frame center, and it holds the [rotatedBounds] of the frame. Without rotation it is 1.
     */
    fun editorViewScale(width: Double, height: Double, rotationDeg: Double): Double {
        if (width <= 0.0 || height <= 0.0) return 1.0
        val bounds = rotatedBounds(CropRect(0.0, 0.0, width, height), rotationDeg)
        return max(1.0, max(bounds.width / width, bounds.height / height))
    }

    fun clampInside(rect: CropRect, bounds: CropRect, aspect: Double): CropRect {
        if (bounds.width <= 0.0 || bounds.height <= 0.0) return CropRect(bounds.x, bounds.y, 0.0, 0.0)
        val safeAspect = aspect.takeIf { it > 0.0 } ?: (bounds.width / bounds.height)
        val maxWidthByHeight = bounds.height * safeAspect
        val maxWidth = min(bounds.width, maxWidthByHeight).coerceAtLeast(MIN_OVERLAY_SIZE.coerceAtMost(bounds.width))
        val minWidth = MIN_OVERLAY_SIZE.coerceAtMost(maxWidth)
        val width = rect.width.coerceIn(minWidth, maxWidth)
        val rawHeight = width / safeAspect
        val height = if (rawHeight > bounds.height && rawHeight - bounds.height <= EPSILON) {
            bounds.height
        } else {
            rawHeight.coerceAtMost(bounds.height)
        }
        val x = coerceCoordinate(rect.x, bounds.x, bounds.x + bounds.width - width)
        val y = coerceCoordinate(rect.y, bounds.y, bounds.y + bounds.height - height)
        return CropRect(x, y, width, height)
    }

    /**
     * Fits [rect] to [frame] when the frame content turns by [rotationDeg].
     * The size is not larger than [frame]. A larger [rect] shrinks about its center.
     * The position can be anywhere inside the [rotatedBounds] of [frame]. So the rectangle can go past the frame
     * edges to the corners of the rotated content, and it can show the fill color around them.
     */
    fun fitToRotatedFrame(rect: CropRect, frame: CropRect, rotationDeg: Double, aspect: Double): CropRect =
        clampInside(shrinkAboutCenter(rect, frame.width, aspect), rotatedBounds(frame, rotationDeg), aspect)

    private fun coerceCoordinate(value: Double, minimum: Double, maximum: Double): Double {
        return if (maximum <= minimum) minimum else value.coerceIn(minimum, maximum)
    }

    fun centeredResize(start: CropRect, bounds: CropRect, aspect: Double, widthDelta: Double): CropRect {
        val centerX = start.x + start.width / 2.0
        val centerY = start.y + start.height / 2.0
        val targetWidth = start.width + widthDelta
        val resized = CropRect(
            x = centerX - targetWidth / 2.0,
            y = centerY - (targetWidth / aspect) / 2.0,
            width = targetWidth,
            height = targetWidth / aspect,
        )
        return clampInside(resized, bounds, aspect)
    }

    fun largestCenteredInscribedRect(
        width: Double,
        height: Double,
        rotationDeg: Double,
        aspect: Double,
    ): CropRect {
        if (width <= 0.0 || height <= 0.0) return CropRect(0.0, 0.0, 0.0, 0.0)
        val safeAspect = aspect.takeIf { it > 0.0 } ?: (width / height)
        val normalizedRotation = normalizeRotation(rotationDeg.toFloat()).toDouble()
        if (abs(normalizedRotation) < 0.001 || abs(abs(normalizedRotation) - 180.0) < 0.001) {
            return CropRect(0.0, 0.0, width, height)
        }

        val radians = Math.toRadians(normalizedRotation)
        val cos = cos(radians)
        val sin = sin(radians)
        val halfW = width / 2.0
        val halfH = height / 2.0

        fun fits(candidateWidth: Double): Boolean {
            val candidateHeight = candidateWidth / safeAspect
            val points = arrayOf(
                -candidateWidth / 2.0 to -candidateHeight / 2.0,
                candidateWidth / 2.0 to -candidateHeight / 2.0,
                candidateWidth / 2.0 to candidateHeight / 2.0,
                -candidateWidth / 2.0 to candidateHeight / 2.0,
            )
            return points.all { (x, y) ->
                val unrotatedX = x * cos + y * sin
                val unrotatedY = -x * sin + y * cos
                abs(unrotatedX) <= halfW + 0.001 && abs(unrotatedY) <= halfH + 0.001
            }
        }

        var low = 0.0
        var high = min(width, height * safeAspect)
        repeat(40) {
            val mid = (low + high) / 2.0
            if (fits(mid)) low = mid else high = mid
        }

        val resultWidth = max(MIN_OVERLAY_SIZE.coerceAtMost(width), low)
        val resultHeight = (resultWidth / safeAspect).coerceAtMost(height)
        return CropRect(
            x = (width - resultWidth) / 2.0,
            y = (height - resultHeight) / 2.0,
            width = resultWidth,
            height = resultHeight,
        )
    }

    fun normalizeRotation(degrees: Float): Float {
        var value = degrees
        while (value > 180.0f) value -= 360.0f
        while (value < -180.0f) value += 360.0f
        return value
    }

    fun snapRotation(degrees: Float, forceSnap: Boolean): Float {
        val normalized = normalizeRotation(degrees)
        val targets = floatArrayOf(-180.0f, -90.0f, 0.0f, 90.0f, 180.0f)
        val closest = targets.minBy { abs(it - normalized) }
        return if (forceSnap || abs(closest - normalized) <= 2.0f) {
            normalizeRotation(closest)
        } else {
            normalized
        }
    }
}
