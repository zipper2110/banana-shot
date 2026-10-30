package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.RecordingAnalytics
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultAdjustVideoPresenterAnalyticsTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val analytics = RecordingAnalytics()
    private val presenter = DefaultAdjustVideoPresenter(adjustments, analytics)

    @AfterTest
    fun tearDown() {
        adjustments.close()
        scheduler.shutdownNow()
    }

    @Test
    fun `a visit with many transform changes counts one change when the tab closes`() {
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1(zoom = 1.2f)))
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1(zoom = 1.4f)))
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1(zoom = 1.4f, rotationDeg = 90f)))
        assertEquals(emptyList(), analytics.events)

        presenter.onDeactivated()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CropRotateChanged), analytics.events)
    }

    @Test
    fun `a visit without an effective change counts nothing`() {
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.ChangeTransform(AdjustmentsV1()))
        presenter.onIntent(AdjustVideoIntent.ResetTransform)
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1()))
        presenter.onIntent(AdjustVideoIntent.ResetColors)
        presenter.onDeactivated()
        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun `a reset with an effect counts as a change`() {
        adjustments.set(AdjustmentsV1(rotationDeg = 90f))
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.ResetTransform)
        presenter.onDeactivated()
        presenter.onActivated()
        presenter.onDeactivated()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CropRotateChanged), analytics.events)
    }

    @Test
    fun `a color drag counts one change when the user releases the slider`() {
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.1f), final = false))
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.2f), final = false))
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.2f)))
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.2f)))
        presenter.onIntent(AdjustVideoIntent.ResetColors)
        presenter.onIntent(AdjustVideoIntent.ResetColors)
        assertEquals(1.0f, adjustments.get().brightness)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ColorChanged, AnalyticsEvent.ColorChanged), analytics.events)
    }

    @Test
    fun `a switch of the sub-tab counts as a shown tab of the user`() {
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.COLORS))
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.TRANSFORM))
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.TRANSFORM))
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.COLORS))
        // The tab opens on the Trim sub-tab, so the first switch to Colors also counts (B-60).
        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.TabShown(AnalyticsEvent.Tab.COLORS, byUser = true),
                AnalyticsEvent.TabShown(AnalyticsEvent.Tab.CROP_ROTATE, byUser = true),
                AnalyticsEvent.TabShown(AnalyticsEvent.Tab.COLORS, byUser = true),
            ),
            analytics.events,
        )
        assertEquals(AdjustSection.COLORS, presenter.section)
    }
}
