package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScoringPlaybackBarTest {
    private class Calls {
        val list = mutableListOf<String>()
    }

    private fun bar(calls: Calls) = ScoringPlaybackBar(
        onTogglePlay = { calls.list += "play" },
        onNudge = { calls.list += "nudge:$it" },
        onScrub = { calls.list += "scrub:$it" },
        onSpeedIndex = { calls.list += "speed:$it" },
        onToggleFrameStep = { calls.list += "frame-step" },
        onAddComment = { calls.list += "add-comment" },
    )

    @Test
    fun transportButtonsSeekAndPlay() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val bar = bar(calls)
            for (name in listOf("scoring-seek-back-5s", "scoring-seek-back-1s", "scoring-play-pause", "scoring-seek-forward-1s", "scoring-seek-forward-5s")) {
                val button = bar.findNamed(name) as? JButton
                assertNotNull(button, name)
                assertFalse(button.isFocusable, "$name must not take the focus from the player")
                button.doClick()
            }
            assertEquals(listOf("nudge:-5000", "nudge:-1000", "play", "nudge:1000", "nudge:5000"), calls.list)
        }
    }

    @Test
    fun speedIsAtTheLeftAndFrameStepAtTheRight() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val bar = bar(calls)
            bar.setSize(1100, bar.preferredSize.height)
            bar.doLayout()

            val combo = bar.speed.combo
            assertEquals(listOf("2×", "1.5×", "1.25×", "1×", "0.5×"), (0 until combo.itemCount).map { combo.getItemAt(it) })
            bar.speed.setSpeedIndex(3)
            assertEquals(emptyList(), calls.list, "A speed that the tab shows does not call back")
            combo.selectedIndex = 1
            assertEquals(listOf("speed:1"), calls.list)

            bar.frameStep.doClick()
            assertEquals("frame-step", calls.list.last())
            assertFalse(bar.frameStep.on)
            bar.frameStep.on = true
            assertTrue(bar.frameStep.model.isSelected)

            val transport = bar.findNamed("scoring-video-controls")!!
            assertTrue(bar.speed.x < transport.x, "The speed is at the left of the transport")
            assertTrue(bar.frameStep.x > transport.x + transport.width, "Frame step is at the right of the transport")
            assertTrue(bar.scrub.y < transport.y, "The scrub bar is above the controls")
        }
    }

    @Test
    fun addCommentIsAtTheRightBeforeFrameStep() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val bar = bar(calls)
            bar.setSize(1100, bar.preferredSize.height)
            bar.doLayout()

            val button = bar.findNamed("scoring-add-comment") as? JButton
            assertNotNull(button)
            assertFalse(button.isFocusable, "Comment must not take the focus from the player")
            assertEquals("Comment", button.text)
            button.doClick()
            assertEquals(listOf("add-comment"), calls.list)

            val transport = bar.findNamed("scoring-video-controls")!!
            assertTrue(button.x > transport.x + transport.width, "Comment is at the right of the transport")
            assertTrue(button.x + button.width < bar.frameStep.x, "Comment is at the left of Frame step")
            assertTrue(bar.frameStep.x + bar.frameStep.width <= bar.width - bar.insets.right, "Frame step stays in the bar")

            // A narrow bar shows only the icon; the tooltip keeps the action name.
            bar.setSize(700, bar.preferredSize.height)
            bar.doLayout()
            assertEquals("", button.text)
            assertTrue(button.toolTipText.startsWith("Add a comment"))
            assertTrue(button.x > transport.x + transport.width, "The narrow Comment does not cover the transport")
        }
    }

    @Test
    fun narrowBarHidesTheSpeedCaptionAndKeys() {
        SwingUtilities.invokeAndWait {
            val bar = bar(Calls())
            bar.setSize(1100, bar.preferredSize.height)
            bar.doLayout()
            val wide = bar.speed.preferredSize.width
            assertFalse(bar.speed.compact)

            bar.setSize(700, bar.preferredSize.height)
            bar.doLayout()
            assertTrue(bar.speed.compact)
            assertTrue(bar.speed.preferredSize.width < wide)
        }
    }

    @Test
    fun scrubTimesCountFromThePointStartAndAClickSeeks() {
        SwingUtilities.invokeAndWait {
            val calls = Calls()
            val scrub = bar(calls).scrub
            assertFalse(scrub.hasSegment)

            scrub.setSegment(1_086_900, 1_106_200)
            assertEquals("0:00.0", PointScrubBar.formatRelative(0))
            assertEquals("0:19.3", PointScrubBar.formatRelative(1_106_200 - 1_086_900))
            assertEquals("1:05.2", PointScrubBar.formatRelative(65_250))
            assertEquals(0.0, scrub.fraction)

            scrub.setSize(600, PointScrubBar.HEIGHT)
            scrub.setPosition(1_096_550)
            assertEquals(0.5, scrub.fraction, 0.001)

            // A press at the right end of the track seeks to the last millisecond of the point.
            val right = scrub.trackBounds()[1]
            val press = MouseEvent(scrub, MouseEvent.MOUSE_PRESSED, 0, 0, right + 2, 16, 1, false, MouseEvent.BUTTON1)
            scrub.mouseListeners.forEach { it.mousePressed(press) }
            assertEquals(listOf("scrub:1106199"), calls.list)
        }
    }

    private fun Component.findNamed(name: String): Component? {
        if (this.name == name) return this
        if (this is Container) components.forEach { child -> child.findNamed(name)?.let { return it } }
        return null
    }
}
