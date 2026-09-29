package org.litvin.ui.tabs.points.ui

import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * The parts of the Points dialogs: a head with a title, form fields, an error line and a footer with buttons.
 * The look comes from the dialogs of design/points-redesign (shared.css, "Dialogs").
 */
internal object PointsDialogKit {
    private val DIALOG_BG = Color(0x1C1C1C)
    private val FOOTER_BG = Color(0x181818)
    private val INPUT_BG = Color(0x121212)
    const val DIALOG_WIDTH = 420

    /** A modal dialog that closes on Escape. */
    fun dialog(parent: Component, title: String): JDialog {
        val owner = SwingUtilities.getWindowAncestor(parent)
        return JDialog(owner as? Window, title, Dialog.ModalityType.APPLICATION_MODAL).apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "points-dialog-cancel")
            rootPane.actionMap.put("points-dialog-cancel", object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) = dispose()
            })
        }
    }

    /** The content of a dialog: the head, the form, and the footer, on the dark dialog background. */
    fun content(head: JComponent, form: JComponent, footer: JComponent): JPanel = object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = Dimension(DIALOG_WIDTH, super.getPreferredSize().height)
    }.apply {
        background = DIALOG_BG
        isOpaque = true
        add(head, BorderLayout.NORTH)
        add(form, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
    }

    /** The title of the dialog, with an optional value on the right, for example the length of a point. */
    fun head(title: String, sub: String? = null): JComponent = JPanel(BorderLayout(10, 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(14, 18, 4, 18)
        add(JLabel(title).apply {
            font = UiKit.font(15f, UiKit.Weight.BOLD)
            foreground = UiKit.FG
        }, BorderLayout.WEST)
        if (sub != null) add(JLabel(sub).apply {
            font = UiKit.font(12f)
            foreground = UiKit.FG_3
        }, BorderLayout.EAST)
    }

    /** The form: the rows one under the other with 12 px between them. */
    fun form(vararg rows: JComponent): JComponent = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(10, 18, 6, 18)
        rows.forEachIndexed { index, row ->
            if (index > 0) add(Box.createVerticalStrut(12))
            row.alignmentX = Component.LEFT_ALIGNMENT
            add(row)
        }
    }

    /** Two fields side by side. */
    fun pair(left: JComponent, right: JComponent): JComponent = JPanel(GridLayout(1, 2, 12, 0)).apply {
        isOpaque = false
        add(left)
        add(right)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    /** A caption above an input, with an optional small note such as "optional". */
    fun field(caption: String, input: JComponent, note: String? = null): JComponent = JPanel(BorderLayout(0, 5)).apply {
        isOpaque = false
        val label = JLabel(caption).apply {
            font = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
            foreground = UiKit.FG_2
        }
        add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            (layout as FlowLayout).alignOnBaseline = true
            add(label)
            if (note != null) add(JLabel("  $note").apply {
                font = UiKit.font(11f)
                foreground = UiKit.FG_3
                // Some slack, because the painted text can be a little wider than the measured text.
                preferredSize = Dimension(preferredSize.width + 4, preferredSize.height)
            })
        }, BorderLayout.NORTH)
        add(input, BorderLayout.CENTER)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    /**
     * A dark input box around [input]. The box paints the rounded background and the border;
     * the border is lime while the input has the focus. [boxHeight] is null for a box that follows the input.
     */
    fun inputBox(input: JTextComponent, boxHeight: Int? = 34, padding: Int = 0): JComponent {
        input.background = INPUT_BG
        input.foreground = UiKit.FG
        input.caretColor = UiKit.FG
        input.selectionColor = Color(161, 254, 0, 70)
        input.selectedTextColor = UiKit.FG
        input.font = UiKit.font(13f)
        input.border = BorderFactory.createEmptyBorder(padding, 10, padding, 10)
        val box = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = UiKit.smooth(g)
                try {
                    val border = if (input.isFocusOwner) UiKit.LIME_LINE else UiKit.LINE_2
                    UiKit.paintBox(g2, 0, 0, width, height, 4, INPUT_BG, border)
                } finally {
                    g2.dispose()
                }
            }
        }
        box.isOpaque = false
        box.border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        if (input is JTextArea) {
            // A long comment scrolls inside the box, like a textarea of the design.
            box.add(JScrollPane(input).apply {
                border = BorderFactory.createEmptyBorder()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                runCatching { applyDarkScrollbar(this, INPUT_BG) }
            }, BorderLayout.CENTER)
        } else {
            box.add(input, BorderLayout.CENTER)
        }
        if (boxHeight != null) {
            box.preferredSize = Dimension(box.preferredSize.width, boxHeight)
            box.minimumSize = Dimension(0, boxHeight)
        }
        input.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = box.repaint()
            override fun focusLost(e: FocusEvent) = box.repaint()
        })
        return box
    }

    /** The red line for a form error. It keeps its height when it is empty, so the dialog does not move. */
    fun errorLine(): JLabel = JLabel(" ").apply {
        font = UiKit.font(12f)
        foreground = UiKit.ERROR
        preferredSize = Dimension(0, 16)
        maximumSize = Dimension(Int.MAX_VALUE, 16)
    }

    /** The footer: [left] buttons, a flexible gap, then [right] buttons. */
    fun footer(left: List<JButton>, right: List<JButton>): JComponent = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            g.color = FOOTER_BG
            g.fillRect(0, 0, width, height)
            g.color = UiKit.LINE
            g.fillRect(0, 0, width, 1)
        }
    }.apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(14, 18, 14, 18)
        left.forEach { add(it); add(Box.createHorizontalStrut(8)) }
        add(Box.createHorizontalGlue())
        right.forEachIndexed { index, button ->
            if (index > 0) add(Box.createHorizontalStrut(8))
            add(button)
        }
    }
}
