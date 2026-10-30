package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.ui.tabs.adjustments.TrimEnd
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Trim sub-tab (B-60). */
class DefaultAdjustVideoPresenterTrimTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val presenter = DefaultAdjustVideoPresenter(adjustments)
    private val projectDir: File = Files.createTempDirectory("trim-test").toFile()
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

    private fun onEdt(body: () -> Unit) = SwingUtilities.invokeAndWait(body)

    /** Opens a project with points at 5-8 s and 20-25 s. The project has no manifest, so no source video. */
    private fun open() {
        EdlIO.writeForProjectDir(projectDir.absolutePath, EdlV1(listOf(PointV1("a", 5_000, 8_000), PointV1("b", 20_000, 25_000))))
        presenter.attach(view)
        presenter.onIntent(AdjustVideoIntent.LoadProject(File(projectDir, "manifest.json").absolutePath))
        presenter.onActivated()
        presenter.onIntent(AdjustVideoIntent.SelectSection(AdjustSection.TRIM))
        view.effects.clear()
    }

    private fun setAt(ms: Long, end: TrimEnd) {
        presenter.onIntent(AdjustVideoIntent.PlayheadMoved(ms))
        presenter.onIntent(AdjustVideoIntent.SetTrim(end, DURATION))
    }

    @AfterTest
    fun tearDown() {
        adjustments.close()
        scheduler.shutdownNow()
        projectDir.deleteRecursively()
    }

    @Test
    fun `Set here stores the playhead and a time at the source edge clears the trim end`() = onEdt {
        open()
        setAt(3_000, TrimEnd.START)
        setAt(50_000, TrimEnd.END)
        assertEquals(3_000L, adjustments.get().trimStartMs)
        assertEquals(50_000L, adjustments.get().trimEndMs)
        assertEquals(listOf(3_000L, 50_000L), view.state.changePointTimes)

        setAt(DURATION - 50, TrimEnd.END)
        assertNull(adjustments.get().trimEndMs)
        presenter.onIntent(AdjustVideoIntent.ResetTrim)
        assertNull(adjustments.get().trimStartMs)
    }

    @Test
    fun `a start after the end shows an error and changes nothing`() = onEdt {
        open()
        setAt(30_000, TrimEnd.END)
        setAt(29_500, TrimEnd.START)
        assertNull(adjustments.get().trimStartMs)
        assertIs<AdjustVideoViewEffect.ShowError>(view.effects.single())
    }

    @Test
    fun `a trim that cuts off points keeps them and shows the dialog only the first time`() = onEdt {
        open()
        setAt(6_000, TrimEnd.START)
        assertEquals(listOf("a"), view.state.pointsOutsideTrim.map { it.id })
        assertEquals(AdjustVideoViewEffect.TrimCutsPoints(1), view.effects.single())
        assertTrue(adjustments.get().trimWarningShown)
        assertEquals(2, view.state.points.size)

        setAt(22_000, TrimEnd.END)
        assertEquals(listOf("a", "b"), view.state.pointsOutsideTrim.map { it.id })
        assertEquals(1, view.effects.size)

        setAt(1_000, TrimEnd.START)
        setAt(40_000, TrimEnd.END)
        assertEquals(emptyList(), view.state.pointsOutsideTrim)
    }

    @Test
    fun `the Trim sub-tab sends no analytics tab`() {
        assertNull(DefaultAdjustVideoPresenter.analyticsTab(AdjustSection.TRIM))
    }

    private companion object {
        const val DURATION = 60_000L
    }
}
