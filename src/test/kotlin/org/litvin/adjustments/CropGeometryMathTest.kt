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
    fun aRotatedCropCanGrowToTheFrameSize() {
        val frame = CropRect(0.0, 0.0, 1920.0, 1080.0)
        val fitted = CropGeometryMath.fitToRotatedFrame(frame, frame, 5.0, 16.0 / 9.0)
        val tooLarge = CropGeometryMath.fitToRotatedFrame(CropRect(-200.0, -100.0, 2320.0, 1305.0), frame, 5.0, 16.0 / 9.0)

        assertEquals(frame, fitted)
        assertNear(1920.0, tooLarge.width, 0.001)
        assertEquals(frame, CropGeometryMath.overlayFromModel(1920.0, 1080.0, AdjustmentsV1(rotationDeg = 5.0f)))
    }

    @Test
    fun aRotationOfAnAutoFittedCropKeepsTheCornersOut() {
        val fit10 = CropGeometryMath.fitZoom(1920.0, 1080.0, 10.0)
        val start = AdjustmentsV1()

        val rotated = CropGeometryMath.keepCornersOut(start, start.copy(rotationDeg = 10.0f), 1920.0, 1080.0)
        assertEquals(fit10, rotated.zoom)
        assertTrue(fit10 > 1.2f, "fit zoom: $fit10")

        val back = CropGeometryMath.keepCornersOut(rotated, rotated.copy(rotationDeg = 0.0f), 1920.0, 1080.0)
        assertEquals(1.0f, back.zoom)
    }

    @Test
    fun aRotationKeepsALargerZoomAndTheCornersThatTheUserChose() {
        val zoomed = AdjustmentsV1(zoom = 2.0f)
        val rotated = CropGeometryMath.keepCornersOut(zoomed, zoomed.copy(rotationDeg = 10.0f), 1920.0, 1080.0)
        assertEquals(2.0f, rotated.zoom)

        val grown = AdjustmentsV1(rotationDeg = 10.0f, zoom = 1.05f)
        val turned = CropGeometryMath.keepCornersOut(grown, grown.copy(rotationDeg = 12.0f), 1920.0, 1080.0)
        assertEquals(1.05f, turned.zoom)
    }

    @Test
    fun anOlderProjectKeepsItsCropAfterTheConversion() {
        val older = AdjustmentsV1(
            rotationDeg = 6.0f,
            panX = 0.7f,
            panY = -0.4f,
            geometryKeys = listOf(GeometryKeyV1(atMs = 5_000, zoom = 1.3f, panX = -1.0f, rotationDeg = -4.0f)),
            version = 1,
        )
        val converted = CropGeometryMath.withCurrentVersion(older, 3840.0, 2160.0)

        assertEquals(AdjustmentsV1.CURRENT_VERSION, converted.version)
        for (index in -1 until older.geometryKeys.size) {
            val before = GeometryPlan.of(GeometryTimeline.valuesOf(older, index), 3840, 2160)
            val after = GeometryPlan.of(GeometryTimeline.valuesOf(converted, index), 3840, 2160)
            assertEquals(before.rotationDeg, after.rotationDeg)
            assertNear(before.crop.x, after.crop.x, 2.0 / 3840.0)
            assertNear(before.crop.y, after.crop.y, 2.0 / 2160.0)
            assertNear(before.crop.width, after.crop.width, 2.0 / 3840.0)
        }
        // The older rules limit the size with a rotation, so the converted zoom is above 100%.
        assertTrue(converted.zoom > 1.0f, "zoom: ${converted.zoom}")
        assertEquals(converted, CropGeometryMath.withCurrentVersion(converted, 3840.0, 2160.0))
    }

    @Test
    fun panMovesTheCropPastTheFrameEdgeToTheEdgeOfTheRotatedFrame() {
        val plan = GeometryPlan.of(AdjustmentsV1(zoom = 1.3f, panX = 1.0f, rotationDeg = 5.0f), 3840, 2160)
        val bounds = CropGeometryMath.rotatedBounds(CropRect(0.0, 0.0, 3840.0, 2160.0), 5.0)

        // The even-pixel snap can leave up to 4 source pixels.
        assertNear((bounds.x + bounds.width) / 3840.0, plan.crop.x + plan.crop.width, 4.0 / 3840.0)
        assertTrue(plan.crop.x + plan.crop.width > 1.0)
        assertNear(1.0 / 1.3, plan.crop.width, 2.0 / 3840.0)
    }

    @Test
    fun withoutRotationThePanKeepsTheCropInsideTheFrame() {
        val plan = GeometryPlan.of(AdjustmentsV1(zoom = 2.0f, panX = 1.0f, panY = -1.0f), 3840, 2160)

        assertEquals(CropRect(0.5, 0.5, 0.5, 0.5), plan.crop)
        assertEquals(Pair(0.0, 0.0), plan.padding)
    }

    @Test
    fun anAutoFittedRotationCanPanPastTheFrameEdge() {
        val zoom = CropGeometryMath.fitZoom(1920.0, 1080.0, 10.0)
        val centered = CropGeometryMath.overlayFromModel(1920.0, 1080.0, AdjustmentsV1(zoom = zoom, rotationDeg = 10.0f))
        val panned = CropGeometryMath.overlayFromModel(1920.0, 1080.0, AdjustmentsV1(zoom = zoom, rotationDeg = 10.0f, panY = 1.0f))
        val bounds = CropGeometryMath.rotatedBounds(CropRect(0.0, 0.0, 1920.0, 1080.0), 10.0)

        assertEquals(centered.width, panned.width)
        assertNear(bounds.y, panned.y, 0.001)
        assertTrue(panned.y < 0.0)
    }

    @Test
    fun modelFromOverlayReadsBackACropPastTheFrameEdge() {
        val previous = AdjustmentsV1(rotationDeg = 8.0f)
        val start = CropGeometryMath.overlayFromModel(1920.0, 1080.0, previous.copy(zoom = 1.4f))
        val moved = start.copy(x = -60.0, y = 1080.0 - start.height + 40.0)

        val model = CropGeometryMath.modelFromOverlay(1920.0, 1080.0, moved, previous)
        val back = CropGeometryMath.overlayFromModel(1920.0, 1080.0, model)

        assertNear(moved.x, back.x, 0.5)
        assertNear(moved.y, back.y, 0.5)
        assertNear(moved.width, back.width, 0.5)
    }

    @Test
    fun editorViewHoldsTheRotatedFrame() {
        assertEquals(1.0, CropGeometryMath.editorViewScale(1920.0, 1080.0, 0.0))
        assertEquals(1.0, CropGeometryMath.editorViewScale(1920.0, 1080.0, 180.0))
        assertNear(16.0 / 9.0, CropGeometryMath.editorViewScale(1920.0, 1080.0, 90.0), 1e-6)
        val bounds = CropGeometryMath.rotatedBounds(CropRect(0.0, 0.0, 1920.0, 1080.0), 5.0)
        assertNear(bounds.height / 1080.0, CropGeometryMath.editorViewScale(1920.0, 1080.0, 5.0), 1e-9)
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
