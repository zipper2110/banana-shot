package org.litvin.adjustments

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropGeometryMathTest {
    @Test
    fun overlayModelRoundTripPreservesTransform() {
        val original = AdjustmentsV1(zoom = 2.0f, panX = 0.5f, panY = -0.25f, rotationDeg = 12.5f)
        val overlay = CropGeometryMath.overlayFromModel(1920.0, 1080.0, original)
        val roundTrip = CropGeometryMath.modelFromOverlay(1920.0, 1080.0, overlay, original)

        assertNear(original.zoom, roundTrip.zoom, 0.01f)
        assertNear(original.panX, roundTrip.panX, 0.01f)
        assertNear(original.panY, roundTrip.panY, 0.01f)
        assertEquals(original.rotationDeg, roundTrip.rotationDeg)
    }

    @Test
    fun clampInsideKeepsOverlayWithinBoundsAndAspect() {
        val bounds = CropRect(10.0, 20.0, 640.0, 360.0)
        val outside = CropRect(-100.0, -100.0, 900.0, 100.0)
        val clamped = CropGeometryMath.clampInside(outside, bounds, 16.0 / 9.0)

        assertTrue(clamped.x >= bounds.x)
        assertTrue(clamped.y >= bounds.y)
        assertTrue(clamped.x + clamped.width <= bounds.x + bounds.width + 0.001)
        assertTrue(clamped.y + clamped.height <= bounds.y + bounds.height + 0.001)
        assertTrue(abs((clamped.width / clamped.height) - (16.0 / 9.0)) < 0.001)
    }

    @Test
    fun rotatedInscribedRectShrinksAndStaysCentered() {
        val rect = CropGeometryMath.largestCenteredInscribedRect(1920.0, 1080.0, 45.0, 16.0 / 9.0)

        assertTrue(rect.width < 1920.0)
        assertTrue(rect.height < 1080.0)
        assertNear(1920.0 / 2.0, rect.x + rect.width / 2.0, 0.5)
        assertNear(1080.0 / 2.0, rect.y + rect.height / 2.0, 0.5)
    }

    @Test
    fun clampInsideToleratesNearEqualBoundsAfterRotation() {
        val bounds = CropRect(
            x = 231.283734500025,
            y = 231.283734500025,
            width = 420.0,
            height = 231.28373450002493
        )
        val rect = CropRect(
            x = 231.283734500025,
            y = 231.283734500025,
            width = 411.1719724444888,
            height = 231.283734500025
        )

        val clamped = CropGeometryMath.clampInside(rect, bounds, 16.0 / 9.0)

        assertNear(bounds.y, clamped.y, 0.000001)
        assertTrue(clamped.height <= bounds.height + 0.000001)
    }

    @Test
    fun rotationWithoutPanKeepsTheCenteredInscribedRect() {
        val frame = CropRect(0.0, 0.0, 1920.0, 1080.0)
        val fitted = CropGeometryMath.fitToRotatedFrame(frame, frame, 5.0, 16.0 / 9.0)
        val inscribed = CropGeometryMath.largestCenteredInscribedRect(1920.0, 1080.0, 5.0, 16.0 / 9.0)

        assertNear(inscribed.x, fitted.x, 0.001)
        assertNear(inscribed.y, fitted.y, 0.001)
        assertNear(inscribed.width, fitted.width, 0.001)
    }

    @Test
    fun panMovesTheCropPastTheRotatedFrameToTheFrameEdge() {
        val plan = GeometryPlan.of(AdjustmentsV1(zoom = 1.3f, panX = 1.0f, rotationDeg = 5.0f), 3840, 2160)

        // The even-pixel snap can leave up to 4 source pixels. The old clamp stopped near 0.934.
        assertNear(1.0, plan.crop.x + plan.crop.width, 4.0 / 3840.0)
        assertNear(1.0 / 1.3, plan.crop.width, 2.0 / 3840.0)
    }

    @Test
    fun zoomBelowOneGivesTheFullFrame() {
        val full = CropGeometryMath.overlayFromModel(1600.0, 900.0, AdjustmentsV1(zoom = 1.0f))
        val small = CropGeometryMath.overlayFromModel(1600.0, 900.0, AdjustmentsV1(zoom = 0.3f, panX = 0.5f))
        assertEquals(full, small)
        assertEquals(CropRect(0.0, 0.0, 1600.0, 900.0), full)

        val model = CropGeometryMath.modelFromOverlay(1600.0, 900.0, full, AdjustmentsV1())
        assertEquals(AdjustmentsV1.MIN_ZOOM, model.zoom)
    }

    @Test
    fun rotationSnapsNearCardinalAngles() {
        assertEquals(90.0f, CropGeometryMath.snapRotation(88.5f, forceSnap = false))
        assertEquals(90.0f, CropGeometryMath.snapRotation(74.0f, forceSnap = true))
        assertEquals(180.0f, CropGeometryMath.normalizeRotation(180.0f))
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $actual to be within $tolerance of $expected")
    }

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $actual to be within $tolerance of $expected")
    }
}
