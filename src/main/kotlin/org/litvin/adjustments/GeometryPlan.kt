package org.litvin.adjustments

import kotlin.math.abs

/**
 * The output geometry for a source frame: rotate the frame clockwise about its center (the frame size
 * does not change), then crop [crop]. The preview shader and the FFmpeg export both use this plan.
 *
 * [crop] is a fraction of the frame size, in the space of the rotated frame. It can go past the frame edges
 * (x or y below 0, or x + width above 1). The areas outside the rotated frame get the fill color.
 */
data class GeometryPlan(
    val rotationDeg: Double,
    val crop: CropRect,
) {
    val hasRotation: Boolean get() = abs(rotationDeg) >= ROTATION_EPSILON_DEG
    val hasCrop: Boolean get() = crop != FULL_FRAME
    val isIdentity: Boolean get() = !hasRotation && !hasCrop

    /**
     * The room that the crop needs on each side of the frame, as a fraction of the frame: x on the left and the right,
     * y on the top and the bottom. It is 0 when the crop is inside the frame.
     */
    val padding: Pair<Double, Double>
        get() = Pair(
            maxOf(0.0, -crop.x, crop.x + crop.width - 1.0).takeIf { it > CROP_EPSILON } ?: 0.0,
            maxOf(0.0, -crop.y, crop.y + crop.height - 1.0).takeIf { it > CROP_EPSILON } ?: 0.0,
        )

    /** The crop in whole pixels of a [width] x [height] frame. Exact for the frame size that built this plan. */
    fun cropPixels(width: Int, height: Int): IntArray = intArrayOf(
        Math.round(crop.width * width).toInt(),
        Math.round(crop.height * height).toInt(),
        Math.round(crop.x * width).toInt(),
        Math.round(crop.y * height).toInt(),
    )

    companion object {
        private const val ROTATION_EPSILON_DEG = 0.001
        private const val CROP_EPSILON = 1e-4

        val FULL_FRAME = CropRect(0.0, 0.0, 1.0, 1.0)
        val IDENTITY = GeometryPlan(0.0, FULL_FRAME)

        /**
         * The area that the Transform editor shows, as a fraction of a [width] x [height] frame: the frame and the
         * corners of the rotated frame around it ([CropGeometryMath.editorViewScale]).
         */
        fun editorView(rotationDeg: Double, width: Int, height: Int): CropRect {
            val scale = CropGeometryMath.editorViewScale(width.toDouble(), height.toDouble(), rotationDeg)
            val start = (1.0 - scale) / 2.0
            return CropRect(start, start, scale, scale)
        }

        /**
         * Builds the plan for a [width] x [height] frame, with the same math as the Transform editor.
         * The crop rectangle has the frame aspect. Its size fits inside the rotated frame, and a pan can move it
         * anywhere inside the bounding box of the rotated frame.
         */
        fun of(adjustments: AdjustmentsV1, width: Int, height: Int): GeometryPlan {
            val rotation = CropGeometryMath.normalizeRotation(adjustments.rotationDeg.coerceIn(-180.0f, 180.0f)).toDouble()
            if (width <= 0 || height <= 0) return GeometryPlan(rotation, FULL_FRAME)
            val w = width.toDouble()
            val h = height.toDouble()
            val clamped = CropGeometryMath.overlayFromModel(w, h, adjustments.copy(rotationDeg = rotation.toFloat()))
            val normalized = CropRect(clamped.x / w, clamped.y / h, clamped.width / w, clamped.height / h)
            if (isFullFrame(normalized)) return GeometryPlan(rotation, FULL_FRAME)
            // Snap to even source pixels: FFmpeg crops 4:2:0 video at even whole pixels.
            // The preview uses the same snapped rectangle, so both crop the same pixels.
            val canvas = CropGeometryMath.rotatedBounds(CropRect(0.0, 0.0, w, h), rotation)
            val pixels = snapToEvenPixels(clamped, width, height, canvas)
            return GeometryPlan(
                rotation,
                CropRect(pixels.x / w, pixels.y / h, pixels.width / w, pixels.height / h),
            )
        }

        /**
         * Width and height round down, so the crop does not grow past the fitted size. x and y round to nearest,
         * and the crop stays inside [canvas], the bounding box of the rotated frame.
         */
        private fun snapToEvenPixels(rect: CropRect, width: Int, height: Int, canvas: CropRect): CropRect {
            fun even(value: Double) = Math.round(value / 2.0) * 2.0
            fun evenDown(value: Double) = Math.floor(value / 2.0 + 1e-9) * 2.0
            fun evenUp(value: Double) = Math.ceil(value / 2.0 - 1e-9) * 2.0
            fun coerce(value: Double, minimum: Double, maximum: Double) = if (maximum <= minimum) minimum else value.coerceIn(minimum, maximum)
            val snappedWidth = evenDown(rect.width).coerceIn(2.0, width.toDouble())
            val snappedHeight = evenDown(rect.height).coerceIn(2.0, height.toDouble())
            val x = coerce(even(rect.x), evenUp(canvas.x), evenDown(canvas.x + canvas.width) - snappedWidth)
            val y = coerce(even(rect.y), evenUp(canvas.y), evenDown(canvas.y + canvas.height) - snappedHeight)
            return CropRect(x, y, snappedWidth, snappedHeight)
        }

        private fun isFullFrame(rect: CropRect): Boolean =
            abs(rect.x) < CROP_EPSILON && abs(rect.y) < CROP_EPSILON &&
                abs(rect.width - 1.0) < CROP_EPSILON && abs(rect.height - 1.0) < CROP_EPSILON
    }
}
