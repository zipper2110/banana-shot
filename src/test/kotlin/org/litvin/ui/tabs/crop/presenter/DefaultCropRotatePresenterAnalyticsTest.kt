package org.litvin.ui.tabs.crop.presenter

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.RecordingAnalytics
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultCropRotatePresenterAnalyticsTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val analytics = RecordingAnalytics()
    private val presenter = DefaultCropRotatePresenter(adjustments, analytics)

    @AfterTest
    fun tearDown() {
        adjustments.close()
        scheduler.shutdownNow()
    }

    @Test
    fun `a visit with many transform changes counts one change when the tab closes`() {
        presenter.onActivated()
        presenter.onIntent(CropRotateIntent.ChangeTransform(AdjustmentsV1(zoom = 1.2f)))
        presenter.onIntent(CropRotateIntent.ChangeTransform(AdjustmentsV1(zoom = 1.4f)))
        presenter.onIntent(CropRotateIntent.ChangeTransform(AdjustmentsV1(zoom = 1.4f, rotationDeg = 90f)))
        assertEquals(emptyList(), analytics.events)

        presenter.onDeactivated()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CropRotateChanged), analytics.events)
    }

    @Test
    fun `a visit without an effective change counts nothing`() {
        presenter.onActivated()
        presenter.onIntent(CropRotateIntent.ChangeTransform(AdjustmentsV1()))
        presenter.onIntent(CropRotateIntent.ResetTransform)
        presenter.onDeactivated()
        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun `a reset with an effect counts as a change`() {
        adjustments.set(AdjustmentsV1(rotationDeg = 90f))
        presenter.onActivated()
        presenter.onIntent(CropRotateIntent.ResetTransform)
        presenter.onDeactivated()
        presenter.onActivated()
        presenter.onDeactivated()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CropRotateChanged), analytics.events)
    }
}
