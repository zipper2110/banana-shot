package org.litvin.ui.tabs.points.ui

import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.litvin.ui.commons.PlayButton
import org.litvin.ui.commons.SeekButton
import org.litvin.ui.commons.SpeedControl

class PlaybackBarTest {
    @Test
    fun centersTheTransportAndPutsTheClockLeftAndAddCommentRight() {
        SwingUtilities.invokeAndWait {
            val bar = PlaybackBar(onTogglePlay = {}, onNudge = {}, onAddComment = {}, onSpeedIndex = {})
            bar.setTime(1_316_300, 5_761_000)
            bar.setSize(1400, bar.preferredSize.height)
            bar.layoutRecursively()

            val transport = assertNotNull(bar.findByName("points-video-controls"))
            val clock = assertNotNull(bar.findByName("points-time-panel"))
            val comment = assertNotNull(bar.findByName("points-add-comment"))

            val transportCenter = transport.x + transport.width / 2
            assertTrue(abs(transportCenter - bar.width / 2) <= 1, "transport center=$transportCenter, bar width=${bar.width}")
            assertEquals(16, clock.x)
            assertEquals(bar.width - 16, comment.x + comment.width)
            assertEquals("00:21:56.3", (bar.findByName("points-current-time") as JLabel).text)
            assertTrue(bar.labels().any { it.text == "/ 01:36:01.0" && it.isVisible })

            val speed = bar.descendants().filterIsInstance<SpeedControl>().single()
            assertTrue(speed.isVisible && !speed.compact, "a wide bar shows the full speed control")
            assertTrue(speed.x >= clock.x + clock.width, "speed x=${speed.x}, clock right=${clock.x + clock.width}")
            assertTrue(speed.x + speed.width <= transport.x, "speed right=${speed.x + speed.width}, transport x=${transport.x}")
        }
    }

    @Test
    fun aNarrowBarHidesTheVideoLengthAndTheDirectionIcons() {
        SwingUtilities.invokeAndWait {
            val bar = PlaybackBar(onTogglePlay = {}, onNudge = {}, onAddComment = {}, onSpeedIndex = {})
            bar.setTime(1_000, 5_761_000)
            bar.setSize(860, bar.preferredSize.height)
            bar.layoutRecursively()

            assertFalse(bar.labels().first { it.text.startsWith("/ ") }.isVisible)
            assertTrue(bar.descendants().filterIsInstance<SeekButton>().none { it.showDirection })
            val speed = bar.descendants().filterIsInstance<SpeedControl>().single()
            assertTrue(speed.compact)
        }
    }

    @Test
    fun theSpeedListSendsTheSelectedPresetAndSetSpeedIndexDoesNot() {
        val indexes = mutableListOf<Int>()
        SwingUtilities.invokeAndWait {
            val bar = PlaybackBar(onTogglePlay = {}, onNudge = {}, onAddComment = {}, onSpeedIndex = { indexes += it })
            bar.speed.setSpeedIndex(3)
            bar.speed.combo.selectedIndex = 1
        }
        assertEquals(listOf(1), indexes)
    }

    @Test
    fun seekButtonsSendTheirStepAndTheHotkeyFlashDoesNotSeek() {
        val nudges = mutableListOf<Long>()
        SwingUtilities.invokeAndWait {
            val bar = PlaybackBar(onTogglePlay = {}, onNudge = { nudges += it }, onAddComment = {}, onSpeedIndex = {})
            listOf("points-seek-back-5s", "points-seek-back-1s", "points-seek-forward-1s", "points-seek-forward-5s")
                .forEach { (bar.findByName(it) as SeekButton).doClick() }
            bar.flashSeek(-5_000)
        }
        assertEquals(listOf(-5_000L, -1_000L, 1_000L, 5_000L), nudges)
    }

    @Test
    fun thePlayButtonTooltipFollowsThePlayerState() {
        SwingUtilities.invokeAndWait {
            val bar = PlaybackBar(onTogglePlay = {}, onNudge = {}, onAddComment = {}, onSpeedIndex = {})
            val play = bar.findByName("points-play-pause") as PlayButton
            assertEquals("SPACE — Play", play.toolTipText)
            bar.setPlaying(true)
            assertEquals("SPACE — Pause", play.toolTipText)
        }
    }

    private fun Component.layoutRecursively() {
        if (this is Container) {
            doLayout()
            components.forEach { it.layoutRecursively() }
        }
    }

    private fun Component.findByName(target: String): Component? = descendants().firstOrNull { it.name == target }

    private fun Component.labels(): List<JLabel> = descendants().filterIsInstance<JLabel>()

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }
}
