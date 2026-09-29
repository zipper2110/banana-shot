package org.litvin.ui.tabs.adjustments

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.ui.commons.ResetAllButton
import org.litvin.ui.commons.VideoPlaybackBar
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.io.File
import java.util.concurrent.Executors
import java.util.prefs.AbstractPreferences
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwingColorAdjustmentsPanelTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val adjustments = AdjustmentsSession(scheduler, 60_000)

    @AfterEach
    fun tearDown() {
        adjustments.close()
        scheduler.shutdownNow()
    }

    @Test
    fun groupsTheSlidersIntoLightAndColorAndKeepsTheirNames() {
        onEdt {
            val panel = panel()
            val light = panel.descendants().filterIsInstance<ColorGroupCard>().first { it.group == ColorGroup.LIGHT }
            val color = panel.descendants().filterIsInstance<ColorGroupCard>().first { it.group == ColorGroup.COLOR }

            assertEquals(
                listOf("colors-brightness", "colors-contrast", "colors-shadows", "colors-highlights"),
                light.rows.map { it.slider.name },
            )
            assertEquals(listOf("colors-saturation", "colors-temperature"), color.rows.map { it.slider.name })
            assertEquals("Temperature", (panel.find("colors-temperature-row") as ColorRow).control.label)
            ColorControl.entries.forEach { control ->
                val slider = panel.find(control.componentName) as JSlider
                assertEquals(-100, slider.minimum)
                assertEquals(100, slider.maximum)
                assertEquals(0, slider.value)
            }
            panel.close()
        }
    }

    @Test
    fun showsSignedValuesAndCountsTheChangedControls() {
        onEdt {
            val panel = panel()
            val status = panel.find("colors-status") as JLabel
            val reset = panel.find("colors-reset") as ResetAllButton
            assertEquals("All at default", status.text)
            assertTrue(reset.quiet)

            (panel.find("colors-brightness") as JSlider).value = 6
            (panel.find("colors-highlights") as JSlider).value = -24

            assertEquals("+6", (panel.find("colors-brightness-row") as ColorRow).valueText)
            assertEquals("−24", (panel.find("colors-highlights-row") as ColorRow).valueText)
            assertEquals("0", (panel.find("colors-contrast-row") as ColorRow).valueText)
            assertEquals("2 of 6 changed", status.text)
            assertFalse(reset.quiet)
            panel.close()
        }
    }

    @Test
    fun valueFieldsShowTheValueAndStepWithUpAndDown() {
        onEdt {
            val panel = panel()
            val slider = panel.find("colors-contrast") as JSlider
            val field = panel.find("colors-contrast-value") as JTextField
            assertEquals("0", field.text)

            slider.value = -24
            assertEquals("−24", field.text)

            val up = field.getInputMap(JComponent.WHEN_FOCUSED)[KeyStroke.getKeyStroke("pressed UP")]
            val release = field.getInputMap(JComponent.WHEN_FOCUSED)[KeyStroke.getKeyStroke("released UP")]
            field.actionMap[up].actionPerformed(ActionEvent(field, ActionEvent.ACTION_PERFORMED, "up"))
            field.actionMap[release].actionPerformed(ActionEvent(field, ActionEvent.ACTION_PERFORMED, "up"))
            assertEquals(-23, slider.value)
            assertEquals("−23", field.text)
            ColorControl.entries.forEach { control -> assertTrue(panel.find("${control.componentName}-value") is JTextField) }
            panel.close()
        }
    }

    @Test
    fun sliderChangesGoToTheSessionAndResetAllRestoresTheDefaults() {
        lateinit var panel: SwingColorAdjustmentsPanel
        onEdt {
            panel = panel()
            panel.onActivated()
            (panel.find("colors-brightness") as JSlider).value = 30
            (panel.find("colors-temperature") as JSlider).value = -40
        }
        assertEquals(1.3f, adjustments.get().brightness, 0.0001f)
        assertEquals(-0.4f, adjustments.get().whiteBalance!!.temperature, 0.0001f)

        onEdt { (panel.find("colors-reset") as ResetAllButton).doClick() }
        // The session and the panel deliver the change with invokeLater.
        onEdt { }
        onEdt { }

        assertEquals(AdjustmentsV1().brightness, adjustments.get().brightness)
        onEdt {
            assertEquals(0, (panel.find("colors-brightness") as JSlider).value)
            assertEquals(0, (panel.find("colors-temperature") as JSlider).value)
            assertEquals("All at default", (panel.find("colors-status") as JLabel).text)
            panel.close()
        }
    }

    @Test
    fun theNoticeAndTheTooltipNoteShowOnlyWithoutLivePreview() {
        onEdt {
            val supported = panel(adjustSupported = true)
            assertFalse(supported.find("colors-live-preview-notice")!!.isVisible)
            assertFalse((supported.find("colors-brightness") as JSlider).toolTipText.contains("Live preview"))
            supported.close()

            val unsupported = panel(adjustSupported = false)
            assertTrue(unsupported.find("colors-live-preview-notice")!!.isVisible)
            ColorControl.entries.forEach { control ->
                val tip = (unsupported.find(control.componentName) as JSlider).toolTipText
                assertTrue(tip.startsWith(control.tooltip), tip)
                assertTrue(tip.endsWith(ColorGradePanel.UNSUPPORTED_TIP), tip)
            }
            unsupported.close()
        }
    }

    @Test
    fun thePlaybackBarShowsTheTimeAndSeeksOnlyForTheUser() {
        val seeks = mutableListOf<Long>()
        onEdt {
            val bar = VideoPlaybackBar("colors", "adj-color-transport", onTogglePlay = {}, onSeek = { seeks += it })
            bar.setTime(1_092_000, 5_761_000)
            assertEquals("00:18:12", (bar.find("colors-current-time") as JLabel).text)
            assertEquals("01:36:01", (bar.find("colors-total-time") as JLabel).text)
            assertTrue(seeks.isEmpty())

            bar.seekSlider.value = 5_000
            assertEquals(listOf(2_880_500L), seeks)
            assertEquals("00:48:00", (bar.find("colors-current-time") as JLabel).text)

            assertEquals("SPACE - Play", bar.playButton.toolTipText)
            bar.setPlaying(true)
            assertEquals("SPACE - Pause", bar.playButton.toolTipText)
        }
    }

    @Test
    fun thePlaybackBarGivesTheSeekBarTheFreeWidth() {
        onEdt {
            val bar = VideoPlaybackBar("colors", "adj-color-transport", onTogglePlay = {}, onSeek = {})
            bar.setTime(0, 5_761_000)
            bar.setSize(1120, bar.preferredSize.height)
            bar.doLayout()

            val play = bar.playButton
            val seek = bar.seekSlider
            val total = bar.find("colors-total-time")!!
            assertEquals(16, play.x)
            assertEquals(bar.width - 16, total.x + total.width)
            assertTrue(seek.x > play.x + play.width)
            assertEquals(total.x - 14, seek.x + seek.width)
        }
    }

    @Test
    fun allSixRowsFitWithoutAScrollBarInATypicalWindow() {
        onEdt {
            val grade = ColorGradePanel(liveSupported = true)
            grade.setSize(ColorGradePanel.PANEL_WIDTH, 720)
            grade.layoutRecursively()
            val scroll = grade.descendants().filterIsInstance<javax.swing.JScrollPane>().single()
            assertTrue(scroll.viewport.view.preferredSize.height <= scroll.viewport.height, "the cards need a scroll bar at 720 px")
        }
    }

    private fun panel(adjustSupported: Boolean = true) =
        SwingColorAdjustmentsPanel(FakePlayer(adjustSupported), adjustments, MemoryPreferences())

    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)

    private fun Component.layoutRecursively() {
        if (this is Container) {
            doLayout()
            components.forEach { it.layoutRecursively() }
        }
    }

    private fun Component.descendants(): Sequence<Component> = sequence {
        yield(this@descendants)
        if (this@descendants is Container) components.forEach { yieldAll(it.descendants()) }
    }

    private fun Component.find(name: String): Component? = descendants().firstOrNull { it.name == name }

    private class FakePlayer(private val adjustSupported: Boolean) : SwingMediaPlayer {
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        override fun load(file: File) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seek(ms: Long) = Unit
        override fun setRate(rate: Float) = Unit
        override fun status() = PlayerStatus.UNKNOWN
        override fun currentTimeMs() = 0L
        override fun totalDurationMs() = 0L
        override fun isAdjustSupported() = adjustSupported
        override fun applyColorAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: AdjustmentsV1) = true
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlay(overlay: VideoOverlay?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() = Unit
    }

    private class MemoryPreferences : AbstractPreferences(null, "") {
        private val values = mutableMapOf<String, String>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String) = MemoryPreferences()
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }
}
