package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.ColorKeyV1
import org.litvin.adjustments.GeometryKeyV1
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultAdjustVideoPresenterChangePointsTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val presenter = DefaultAdjustVideoPresenter(adjustments)
    private val view = object : AdjustVideoView {
        var state = AdjustVideoViewState()
        val effects = mutableListOf<AdjustVideoViewEffect>()
        override fun render(state: AdjustVideoViewState) {
            this.state = state
        }
        override fun renderEffect(effect: AdjustVideoViewEffect) {
            effects += effect
        }
    }

    /** The presenter and the session render at once on the EDT. */
    private fun onEdt(body: () -> Unit) = SwingUtilities.invokeAndWait(body)

    @AfterTest
    fun tearDown() {
        adjustments.close()
        scheduler.shutdownNow()
    }

    @Test
    fun `an edit after a change point changes the change point and not the usual values`() = onEdt {
        presenter.attach(view)
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.TRANSFORM))
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1(rotationDeg = 1.0f)))
        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(10_000))
        presenter.onIntent(AdjustVideoIntent.AddChangePoint)
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1(rotationDeg = 3.0f)))

        val stored = adjustments.get()
        assertEquals(1.0f, stored.rotationDeg)
        assertEquals(10_000L, stored.geometryKeys.single().atMs)
        assertEquals(3.0f, stored.geometryKeys.single().rotationDeg)
        assertEquals(3.0f, view.state.editing.rotationDeg)
        assertFalse(view.state.canAddChangePoint)
        assertTrue(view.state.canRemoveChangePoint)
    }

    @Test
    fun `the video size converts an older project and keeps a rotation free of black corners`() = onEdt {
        adjustments.set(AdjustmentsV1(rotationDeg = 5.0f, version = 1))
        presenter.attach(view)
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.VideoSizeKnown(1920, 1080))

        val fit5 = org.litvin.adjustments.CropGeometryMath.fitZoom(1920.0, 1080.0, 5.0)
        assertEquals(AdjustmentsV1.CURRENT_VERSION, adjustments.get().version)
        assertEquals(fit5, adjustments.get().zoom, 0.001f)

        presenter.onIntent(AdjustVideoIntent.ChangeTransform(view.state.editing.copy(rotationDeg = 0.0f)))
        assertEquals(1.0f, adjustments.get().zoom)
    }

    @Test
    fun `the fill color applies to the whole video and not to a change point`() = onEdt {
        presenter.attach(view)
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.TRANSFORM))
        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(10_000))
        presenter.onIntent(AdjustVideoIntent.AddChangePoint)
        presenter.onIntent(AdjustVideoIntent.ChangeBackground("#336699"))
        presenter.onIntent(AdjustVideoIntent.ResetTransform)

        assertEquals("#336699", adjustments.get().backgroundColor)
        assertEquals(0x336699, view.state.editing.backgroundRgb)
        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(0))
        assertEquals(0x336699, view.state.editing.backgroundRgb)
    }

    @Test
    fun `each sub-tab has its own change points`() = onEdt {
        presenter.attach(view)
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(10_000))
        presenter.onIntent(AdjustVideoIntent.AddChangePoint)
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.3f)))
        assertEquals(listOf(10_000L), view.state.changePointTimes)

        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.TRANSFORM))
        assertEquals(emptyList(), view.state.changePointTimes)
        assertTrue(view.state.canAddChangePoint)
        assertFalse(view.state.canRemoveChangePoint)

        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(20_000))
        presenter.onIntent(AdjustVideoIntent.AddChangePoint)
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1(rotationDeg = 2.0f)))

        val stored = adjustments.get()
        assertEquals(listOf(10_000L), stored.colorKeys.map { it.atMs })
        assertEquals(listOf(20_000L), stored.geometryKeys.map { it.atMs })
        assertEquals(1.3f, stored.colorKeys.single().brightness)
        assertEquals(2.0f, stored.geometryKeys.single().rotationDeg)
        assertEquals(1.0f, stored.brightness)
        assertEquals(0.0f, stored.rotationDeg)
        assertEquals(listOf(20_000L), view.state.changePointTimes)

        presenter.onIntent(AdjustVideoIntent.RemoveChangePoint)
        assertEquals(listOf(10_000L), adjustments.get().colorKeys.map { it.atMs })
        assertTrue(adjustments.get().geometryKeys.isEmpty())
    }

    @Test
    fun `reset of one sub-tab keeps the values of the other sub-tab`() = onEdt {
        presenter.attach(view)
        adjustments.set(AdjustmentsV1(brightness = 1.4f, rotationDeg = 5.0f))
        presenter.onActivated()

        presenter.onIntent(AdjustVideoIntent.ResetColors)
        assertEquals(1.0f, adjustments.get().brightness)
        assertEquals(5.0f, adjustments.get().rotationDeg)

        adjustments.set(AdjustmentsV1(brightness = 1.4f, rotationDeg = 5.0f))
        presenter.onIntent(AdjustVideoIntent.ResetTransform)
        assertEquals(1.4f, adjustments.get().brightness)
        assertEquals(0.0f, adjustments.get().rotationDeg)
    }

    @Test
    fun `the controls show other values when the playhead crosses a change point`() = onEdt {
        presenter.attach(view)
        adjustments.set(
            AdjustmentsV1(
                colorKeys = listOf(ColorKeyV1(atMs = 5_000, contrast = 1.5f)),
                geometryKeys = listOf(GeometryKeyV1(atMs = 4_000, rotationDeg = 2.0f)),
            ),
        )
        presenter.onActivated()
        assertEquals(0.0f, view.state.editing.rotationDeg)
        assertEquals(1.0f, view.state.editing.contrast)

        // The Colors sub-tab is open. A transform change point also changes the shown values.
        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(4_500))
        assertEquals(2.0f, view.state.editing.rotationDeg)
        assertEquals(1.0f, view.state.editing.contrast)

        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(6_000))
        assertEquals(2.0f, view.state.editing.rotationDeg)
        assertEquals(1.5f, view.state.editing.contrast)

        presenter.onIntent(AdjustVideoIntent.GoToPreviousChangePoint)
        assertEquals(listOf<AdjustVideoViewEffect>(AdjustVideoViewEffect.SeekTo(5_000)), view.effects)

        presenter.onIntent(AdjustVideoIntent.RemoveChangePoint)
        assertTrue(adjustments.get().colorKeys.isEmpty())
        assertEquals(listOf(4_000L), adjustments.get().geometryKeys.map { it.atMs })
    }
}
