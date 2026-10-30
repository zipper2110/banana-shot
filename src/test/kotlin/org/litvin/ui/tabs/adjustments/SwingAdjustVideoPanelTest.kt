package org.litvin.ui.tabs.adjustments

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.RecordingAnalytics
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.OverlayShape
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.ui.commons.ResetAllButton
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.VideoPlaybackBar
import org.litvin.ui.commons.WrapText
import org.litvin.ui.tabs.adjustments.presenter.AdjustSection
import org.litvin.ui.tabs.adjustments.presenter.DefaultAdjustVideoPresenter
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.Executors
import javax.swing.AbstractButton
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

class SwingAdjustVideoPanelTest {
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
        lateinit var panel: SwingAdjustVideoPanel
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
    fun holdingTheMouseOnTheVideoShowsTheOriginalColorsAndKeepsTheGeometry() {
        adjustments.set { it.copy(zoom = 1.5f) }
        val player = FakePlayer(adjustSupported = true)
        lateinit var panel: SwingAdjustVideoPanel
        onEdt {
            panel = panel(player)
            panel.onActivated()
            (panel.find("adjust-section-colors") as AbstractButton).doClick()
            (panel.find("colors-brightness") as JSlider).value = 30
            (panel.find("colors-saturation") as JSlider).value = -50
        }
        // The session delivers the change with invokeLater.
        onEdt { }
        onEdt { }
        val adjusted = adjustments.get()
        assertEquals(adjusted, player.lastPreview)

        onEdt { player.component.press(MouseEvent.MOUSE_PRESSED) }
        val original = player.lastPreview!!
        assertEquals(AdjustmentsV1().brightness, original.brightness)
        assertEquals(AdjustmentsV1().saturation, original.saturation)
        assertEquals(1.5f, original.zoom)
        assertEquals(adjusted, adjustments.get(), "holding the mouse must not change the saved values")

        onEdt { player.component.press(MouseEvent.MOUSE_RELEASED) }
        assertEquals(adjusted, player.lastPreview)
        onEdt { panel.close() }
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

            assertEquals("Space — Play", bar.playButton.toolTipText)
            bar.setPlaying(true)
            assertEquals("Space — Pause", bar.playButton.toolTipText)
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
            val now = bar.find("colors-current-time")!!
            val total = bar.find("colors-total-time")!!
            assertEquals(16, now.x)
            assertEquals(bar.width - 16, total.x + total.width)
            assertTrue(seek.x > now.x + now.width)
            assertEquals(total.x - 14, seek.x + seek.width)
            assertTrue(play.y + play.height <= seek.y, "the play button is on the row above the seek bar")
        }
    }

    @Test
    fun theSeekButtonsSitAroundThePlayButtonAndSeekByTheirStep() {
        val nudges = mutableListOf<Long>()
        onEdt {
            val bar = VideoPlaybackBar("colors", "adj-color-transport", onTogglePlay = {}, onSeek = {}, onNudge = { nudges += it })
            bar.setTime(0, 5_761_000)
            bar.setSize(1120, bar.preferredSize.height)
            bar.doLayout()

            val names = listOf("colors-seek-back-5s", "colors-seek-back-1s", "colors-seek-forward-1s", "colors-seek-forward-5s")
            val buttons = names.map { bar.find(it) as AbstractButton }
            buttons.forEach { it.doClick() }
            assertEquals(listOf(-5_000L, -1_000L, 1_000L, 5_000L), nudges)

            val play = bar.playButton
            val seek = bar.seekSlider
            assertTrue(buttons[0].x + buttons[0].width < buttons[1].x)
            assertTrue(buttons[1].x + buttons[1].width < play.x)
            assertTrue(play.x + play.width < buttons[2].x)
            assertTrue(buttons[2].x + buttons[2].width < buttons[3].x)
            // The buttons are in the center of their own row, above the seek bar.
            val left = buttons[0].x - 16
            val right = bar.width - 16 - (buttons[3].x + buttons[3].width)
            assertTrue(kotlin.math.abs(left - right) <= 1, "left $left, right $right")
            (buttons + play).forEach { assertTrue(it.y + it.height <= seek.y, "${it.name} is above the seek bar") }
            assertTrue(seek.width > 800, "the seek bar uses the full row")
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

    @Test
    fun analyticsCountOneColorChangeForEachDragAndForEachResetWithAnEffect() {
        val analytics = RecordingAnalytics()
        onEdt {
            val panel = SwingAdjustVideoPanel(FakePlayer(true), DefaultAdjustVideoPresenter(adjustments, analytics))
            val slider = panel.find("colors-brightness") as JSlider
            slider.valueIsAdjusting = true
            slider.value = 10
            slider.value = 20
            slider.valueIsAdjusting = false
            slider.value = 25
            (panel.find("colors-reset") as ResetAllButton).doClick()
            panel.close()
        }
        onEdt { }
        onEdt {
            val panel = SwingAdjustVideoPanel(FakePlayer(true), DefaultAdjustVideoPresenter(adjustments, analytics))
            (panel.find("colors-reset") as ResetAllButton).doClick()
            panel.close()
        }
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ColorChanged, AnalyticsEvent.ColorChanged, AnalyticsEvent.ColorChanged), analytics.events)
    }

    @Test
    fun theSubTabsSwitchTheControlsAndTheCropEditor() {
        val player = FakePlayer(adjustSupported = true)
        onEdt {
            val panel = panel(player)
            val colors = panel.find("adjust-colors")!!
            val transform = panel.find("crop-transform-panel")!!
            // The tab opens on the Trim sub-tab (B-60).
            assertEquals(AdjustSection.TRIM, panel.section)
            assertTrue(panel.find("trim-panel")!!.isVisible)
            assertFalse(colors.isVisible)
            (panel.find("adjust-section-colors") as AbstractButton).doClick()
            assertTrue(colors.isVisible)
            assertFalse(transform.isVisible)
            assertFalse(player.cropEditingOn)
            assertEquals(null, player.shownOverlay)

            (panel.find("adjust-section-transform") as AbstractButton).doClick()
            assertEquals(AdjustSection.TRANSFORM, panel.section)
            assertFalse(colors.isVisible)
            assertTrue(transform.isVisible)
            assertTrue(player.cropEditingOn)

            (panel.find("adjust-section-colors") as AbstractButton).doClick()
            assertEquals(AdjustSection.COLORS, panel.section)
            assertTrue(colors.isVisible)
            assertFalse(player.cropEditingOn)
            assertEquals(null, player.shownOverlay)
            panel.close()
        }
    }

    @Test
    fun eachSubTabHasItsOwnResetAll() {
        adjustments.set(AdjustmentsV1(brightness = 1.3f, rotationDeg = 4.0f))
        onEdt {
            val panel = panel()
            panel.onActivated()
            (panel.find("colors-reset") as ResetAllButton).doClick()
            assertEquals(1.0f, adjustments.get().brightness)
            assertEquals(4.0f, adjustments.get().rotationDeg)

            adjustments.set(AdjustmentsV1(brightness = 1.3f, rotationDeg = 4.0f))
            (panel.find("crop-reset") as ResetAllButton).doClick()
            assertEquals(1.3f, adjustments.get().brightness)
            assertEquals(0.0f, adjustments.get().rotationDeg)
            panel.close()
        }
    }

    @Test
    fun theChangePointsCardIsOutsideTheSubTabsAndShowsTheListOfTheOpenSubTab() {
        onEdt {
            val panel = panel()
            panel.onActivated()
            val card = panel.find("adjust-group-change-points")!!
            assertFalse(panel.find("adjust-colors")!!.descendants().contains(card))
            assertFalse(panel.find("crop-transform-panel")!!.descendants().contains(card))

            (panel.find("adjust-section-colors") as AbstractButton).doClick()
            val status = panel.find("adjust-change-status") as JLabel
            val hint = panel.find("adjust-change-hint") as WrapText
            assertTrue(hint.text.contains("Did the light change?"))
            (panel.find("adjust-change-add") as UiButton).doClick()
            assertEquals(listOf(0L), adjustments.get().colorKeys.map { it.atMs })
            assertEquals(emptyList(), adjustments.get().geometryKeys)
            assertEquals("Change point 1 of 1, at 00:00:00", status.text)

            (panel.find("adjust-section-transform") as AbstractButton).doClick()
            assertEquals("No change points", status.text)
            assertTrue(hint.text.contains("Did the phone move?"))
            panel.close()
        }
    }

    /** The panel with its presenter attached, as when the panel shows in the window. */
    private fun panel(player: FakePlayer = FakePlayer(adjustSupported = true)): SwingAdjustVideoPanel {
        val presenter = DefaultAdjustVideoPresenter(adjustments)
        return SwingAdjustVideoPanel(player, presenter).also { presenter.attach(it) }
    }

    private fun panel(adjustSupported: Boolean) = panel(FakePlayer(adjustSupported))

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

    private fun Component.press(id: Int) = dispatchEvent(
        MouseEvent(this, id, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, 10, 10, 1, false, MouseEvent.BUTTON1),
    )

    private fun Component.find(name: String): Component? = descendants().firstOrNull { it.name == name }

    private class FakePlayer(private val adjustSupported: Boolean) : SwingMediaPlayer {
        var lastPreview: AdjustmentsV1? = null
        var cropEditingOn = false
        var shownOverlay: List<OverlayShape>? = null
        override fun setCropEditing(enabled: Boolean) { cropEditingOn = enabled }
        override fun setEditorOverlay(shapes: List<OverlayShape>?) { shownOverlay = shapes }
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
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) { lastPreview = adj }
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
}
