package org.litvin.ui.commons

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.util.prefs.AbstractPreferences
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HintControllerTest {
    private var frame: JFrame? = null
    private lateinit var first: JButton
    private lateinit var second: JButton
    private val registry = MemoryRegistry()
    private val hints = HintController(registry)

    @BeforeEach
    fun setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        SwingUtilities.invokeAndWait {
            first = JButton("First")
            second = JButton("Second")
            frame = JFrame().apply {
                contentPane.layout = FlowLayout()
                contentPane.add(first)
                contentPane.add(second)
                setSize(800, 400)
                isVisible = true
            }
        }
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeAndWait { frame?.dispose() }
    }

    @Test
    fun aSecondHintWaitsUntilTheFirstCloses() {
        onEdt {
            hints.show(HintId.HELP_BUTTON, first, "first")
            hints.show(HintId.POINT_ROW, second, "second")
        }
        assertEquals(listOf("first"), balloonTexts())
        assertEquals(HintId.HELP_BUTTON, hints.shownHint)

        var closed = 0
        val balloon = balloons().single()
        onEdt { (find(balloon, "hint-balloon-close") as AbstractButton).doClick() }
        assertTrue(registry.isDismissed(HintId.HELP_BUTTON), "The close button closes the hint for good")
        assertEquals(listOf("second"), balloonTexts())

        onEdt { hints.show(HintId.HELP_BUTTON, first, "first") { closed++ } }
        assertEquals(listOf("second"), balloonTexts(), "A closed hint does not show again")
        assertEquals(0, closed)
    }

    @Test
    fun theActionThatTheHintTeachesClosesItForGood() {
        onEdt {
            hints.show(HintId.SCORING_KEYS, first, "keys")
            hints.show(HintId.SERVE_MARK, second, "serve")
            hints.dismiss(HintId.SCORING_KEYS)
        }
        assertTrue(registry.isDismissed(HintId.SCORING_KEYS))
        assertEquals(listOf("serve"), balloonTexts(), "The next hint in the queue shows")
    }

    @Test
    fun aHiddenHintCanShowAgain() {
        onEdt {
            hints.show(HintId.EXPORT_QUEUE, first, "queue")
            hints.hide(HintId.EXPORT_QUEUE)
        }
        assertEquals(emptyList(), balloonTexts())
        assertFalse(registry.isDismissed(HintId.EXPORT_QUEUE))
        assertNull(hints.shownHint)

        onEdt { hints.show(HintId.EXPORT_QUEUE, first, "queue") }
        assertEquals(listOf("queue"), balloonTexts())
    }

    @Test
    fun theBalloonGoesWhenItsAnchorGoesOffTheScreen() {
        onEdt {
            hints.show(HintId.POINT_ROW, first, "row")
            hints.show(HintId.GO_TO_SCORING, second, "scoring")
        }
        onEdt {
            frame!!.contentPane.remove(first)
            frame!!.contentPane.revalidate()
        }
        assertEquals(listOf("scoring"), balloonTexts())
        assertFalse(registry.isDismissed(HintId.POINT_ROW), "The hint shows again at its next trigger")
    }

    @Test
    fun theSameHintMovesToANewAnchor() {
        onEdt {
            hints.show(HintId.POINT_ROW, first, "row")
            hints.show(HintId.POINT_ROW, second, "row")
        }
        val balloons = balloons()
        assertEquals(1, balloons.size)
        var balloonX = 0
        var anchorX = 0
        onEdt {
            balloonX = balloons.single().locationOnScreen.x
            anchorX = second.locationOnScreen.x
        }
        assertTrue(balloonX > anchorX, "The balloon is to the right of the new anchor")
    }

    @Test
    fun aHintWithAnAnchorOffTheScreenDoesNotShow() {
        val hidden = JButton("Hidden")
        onEdt { hints.show(HintId.SERVE_MARK, hidden, "serve") }
        assertEquals(emptyList(), balloonTexts())
        assertNull(hints.shownHint)
    }

    @Test
    fun resetAllShowsTheClosedHintsAgain() {
        onEdt {
            hints.dismiss(HintId.HELP_BUTTON)
            hints.resetAll()
            hints.show(HintId.HELP_BUTTON, first, "help")
        }
        assertEquals(listOf("help"), balloonTexts())
    }

    @Test
    fun noneNeverShowsAHint() {
        onEdt { HintController.NONE.show(HintId.HELP_BUTTON, first, "help") }
        assertEquals(emptyList(), balloonTexts())
    }

    @Test
    fun preferencesKeepOneFlagForEachHint() {
        val preferences = MemoryPreferences()
        val stored = PreferencesHintRegistry(preferences)
        assertFalse(stored.isDismissed(HintId.SERVE_MARK))

        stored.dismiss(HintId.SERVE_MARK)

        assertTrue(PreferencesHintRegistry(preferences).isDismissed(HintId.SERVE_MARK))
        assertFalse(PreferencesHintRegistry(preferences).isDismissed(HintId.SCORING_KEYS))
        assertEquals(true, preferences.getBoolean("hint.serve-mark.dismissed", false))

        PreferencesHintRegistry(preferences).resetAll()

        assertFalse(PreferencesHintRegistry(preferences).isDismissed(HintId.SERVE_MARK))
        assertTrue(HintRegistry.NONE.isDismissed(HintId.SERVE_MARK))
    }

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun balloons(): List<HintBalloon> {
        val found = mutableListOf<HintBalloon>()
        onEdt {
            found += frame!!.ownedWindows
                .filter { it.name == HintBalloon.HOST_NAME && it.isShowing }
                .mapNotNull { (it as? JWindow)?.contentPane as? HintBalloon }
        }
        return found
    }

    private fun balloonTexts(): List<String> = balloons().map { balloon ->
        val label = balloon.components.filterIsInstance<JLabel>().single()
        label.text.substringAfter("px'>").substringBefore("</div>")
    }

    private fun find(root: Container, name: String): Component? {
        for (child in root.components) {
            if (child.name == name) return child
            if (child is Container) find(child, name)?.let { return it }
        }
        return null
    }

    private class MemoryRegistry : HintRegistry {
        private val dismissed = mutableSetOf<HintId>()
        override fun isDismissed(hint: HintId) = hint in dismissed
        override fun dismiss(hint: HintId) {
            dismissed += hint
        }
        override fun resetAll() = dismissed.clear()
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
