package org.litvin.ui.tabs.projects.components

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.text.JTextComponent

/**
 * The parts of the Projects dialogs: a head with a title, form fields, an error line and a footer with buttons.
 * The look comes from the dialogs of design/projects-redesign (shared.css, "Dialogs").
 */
internal object ProjectsDialogKit {
    private val DIALOG_BG = Color(0x1C1C1C)
    private val FOOTER_BG = Color(0x181818)
    private val INPUT_BG = Color(0x121212)

    /** The width of the New project dialog. */
    const val WIDE = 480

    /** The width of a small dialog, for example Rename project. */
    const val NARROW = 420

    /** The content of a dialog: the head, the form, and the footer, on the dark dialog background. */
    fun content(dialogWidth: Int, head: JComponent, form: JComponent, footer: JComponent): JPanel =
        object : JPanel(BorderLayout()) {
            override fun getPreferredSize() = Dimension(dialogWidth, super.getPreferredSize().height)
        }.apply {
            background = DIALOG_BG
            isOpaque = true
            add(head, BorderLayout.NORTH)
            add(form, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }

    /** The title of the dialog. */
    fun head(title: String): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 20, 4, 20)
        add(JLabel(title).apply {
            font = ProjectsUi.font(15f, ProjectsUi.Weight.BOLD)
            foreground = ProjectsUi.FG
        }, BorderLayout.WEST)
    }

    /** The form: the rows one under the other. */
    fun form(vararg rows: JComponent): JComponent = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(10, 20, 8, 20)
        rows.forEach { row ->
            row.alignmentX = Component.LEFT_ALIGNMENT
            add(row)
        }
    }

    /** A caption above an input. */
    fun field(caption: String, input: JComponent): JComponent = JPanel(BorderLayout(0, 5)).apply {
        isOpaque = false
        add(JLabel(caption).apply {
            font = ProjectsUi.font(12f, ProjectsUi.Weight.SEMIBOLD)
            foreground = ProjectsUi.FG_2
        }, BorderLayout.NORTH)
        add(input, BorderLayout.CENTER)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    /**
     * A dark input box around [input]. The box paints the rounded background and the border.
     * The border is lime while the input has the focus, and red while [hasError] returns true.
     */
    fun inputBox(input: JTextComponent, hasError: () -> Boolean = { false }): JComponent {
        input.background = INPUT_BG
        input.foreground = ProjectsUi.FG
        input.caretColor = ProjectsUi.FG
        input.selectionColor = Color(161, 254, 0, 70)
        input.selectedTextColor = ProjectsUi.FG
        input.font = ProjectsUi.font(13f)
        input.border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
        val box = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = ProjectsUi.smooth(g)
                try {
                    val border = when {
                        hasError() -> Color(255, 115, 81, 179)
                        input.isFocusOwner -> ProjectsUi.LIME_LINE
                        else -> ProjectsUi.LINE_2
                    }
                    ProjectsUi.paintBox(g2, 0, 0, width, height, 4, INPUT_BG, border)
                } finally {
                    g2.dispose()
                }
            }
        }
        box.isOpaque = false
        box.border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        box.add(input, BorderLayout.CENTER)
        box.preferredSize = Dimension(box.preferredSize.width, INPUT_HEIGHT)
        box.minimumSize = Dimension(0, INPUT_HEIGHT)
        box.maximumSize = Dimension(Int.MAX_VALUE, INPUT_HEIGHT)
        input.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = box.repaint()
            override fun focusLost(e: FocusEvent) = box.repaint()
        })
        return box
    }

    /** The red line for a form error. It keeps its height when it is empty, so the dialog does not move. */
    fun errorLine(componentName: String): JLabel = JLabel(" ").apply {
        name = componentName
        font = ProjectsUi.font(12f)
        foreground = ProjectsUi.ERROR
        border = BorderFactory.createEmptyBorder(4, 0, 6, 0)
        preferredSize = Dimension(0, 26)
        maximumSize = Dimension(Int.MAX_VALUE, 26)
    }

    /** The footer: a flexible gap, then the [buttons] on the right. */
    fun footer(vararg buttons: JButton): JComponent = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            g.color = FOOTER_BG
            g.fillRect(0, 0, width, height)
            g.color = ProjectsUi.LINE
            g.fillRect(0, 0, width, 1)
        }
    }.apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(14, 20, 14, 20)
        add(Box.createHorizontalGlue())
        buttons.forEachIndexed { index, button ->
            if (index > 0) add(Box.createHorizontalStrut(8))
            add(button)
        }
    }

    private const val INPUT_HEIGHT = 34
}
