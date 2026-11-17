package org.litvin.ui.tabs.points

import org.litvin.ui.tabs.points.components.Keybindings
import org.litvin.ui.tabs.points.components.PointsKeyActions
import org.litvin.ui.commons.AppShortcuts
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PointsKeybindingsTest {
    @Test
    fun enterStartsAPointAndTheOldKeyCAlsoWorks() {
        val panel = JPanel()
        var starts = 0
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() { starts++ }
            override fun cancelPending() {}
            override fun toggleFavoriteSelected() {}
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) {}
            override fun speedUp() {}
            override fun speedDown() {}
        })

        // Enter is the only point start key that the app shows. C is the old key, and it still works.
        assertEquals("ENTER", AppShortcuts.POINT_START.keyStroke)
        listOf("ENTER", "C").forEach { stroke ->
            val name = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(stroke))
            panel.actionMap.get(name).actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, stroke))
        }

        assertEquals(2, starts)
        bindings.uninstall()
    }

    @Test
    fun installsFavoriteHotkeyAction() {
        val panel = JPanel()
        var favoriteToggles = 0
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun cancelPending() {}
            override fun toggleFavoriteSelected() { favoriteToggles++ }
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) {}
            override fun speedUp() {}
            override fun speedDown() {}
        })

        val action = panel.actionMap.get("points.toggleFavorite")
        assertNotNull(action)
        action.actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(1, favoriteToggles)
        bindings.uninstall()
    }

    @Test
    fun shiftArrowActionsNudgeByFiveSeconds() {
        val panel = JPanel()
        val nudges = mutableListOf<Long>()
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun cancelPending() {}
            override fun toggleFavoriteSelected() {}
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) { nudges += deltaMs }
            override fun speedUp() {}
            override fun speedDown() {}
        })

        panel.actionMap.get("points.seekLeftBig")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))
        panel.actionMap.get("points.seekRightBig")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(listOf(-5_000L, 5_000L), nudges)
        bindings.uninstall()
    }

    @Test
    fun upAndDownChangeTheSpeed() {
        val panel = JPanel()
        val calls = mutableListOf<String>()
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun cancelPending() {}
            override fun toggleFavoriteSelected() {}
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) {}
            override fun speedUp() { calls += "up" }
            override fun speedDown() { calls += "down" }
        })

        panel.actionMap.get("points.speedUp")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))
        panel.actionMap.get("points.speedDown")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(listOf("up", "down"), calls)
        bindings.uninstall()
    }

    @Test
    fun escapeCancelsThePendingPoint() {
        val panel = JPanel()
        var cancels = 0
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun cancelPending() { cancels++ }
            override fun toggleFavoriteSelected() {}
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) {}
            override fun speedUp() {}
            override fun speedDown() {}
        })

        val esc = javax.swing.KeyStroke.getKeyStroke("ESCAPE")
        assertEquals("points.cancelPending", panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(esc))
        panel.actionMap.get("points.cancelPending")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(1, cancels)
        bindings.uninstall()
    }
}
