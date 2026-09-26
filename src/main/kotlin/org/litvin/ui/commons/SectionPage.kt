package org.litvin.ui.commons

import org.litvin.ui.UiStyles
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Rectangle
import javax.swing.AbstractButton
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.View
import javax.swing.border.EmptyBorder

/**
 * A page of text and controls for a settings-like window. The items go from top to bottom.
 * The page follows the width of its scroll pane, so that long text wraps.
 */
open class SectionPage(title: String) : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UiStyles.CARD_BG
        border = EmptyBorder(18, 22, 18, 22)
        addItem(JLabel(title).apply {
            foreground = UiStyles.LIME
            font = font.deriveFont(Font.BOLD, 22f)
        }, gapAfter = 10)
    }

    fun subheading(text: String): JLabel = JLabel(text).apply {
        foreground = UiStyles.FG_PRIMARY
        font = font.deriveFont(Font.BOLD, 15f)
    }.also {
        add(Box.createVerticalStrut(12))
        addItem(it, gapAfter = 6)
    }

    fun paragraph(text: String, secondary: Boolean = false): JTextArea = WrappingText(text, reservedWidth = 0).apply {
        isFocusable = false
        wrapStyleWord = true
        foreground = if (secondary) UiStyles.FG_SECONDARY else UiStyles.FG_PRIMARY
    }.also { addItem(it, gapAfter = 8) }

    /** A line for the result of an action. The line is hidden while it has no text. */
    fun statusLine(): JTextArea = WrappingText("", reservedWidth = 0).apply {
        isFocusable = false
        wrapStyleWord = true
        foreground = UiStyles.FG_SECONDARY
        // The gap is a part of the line, so that a hidden line takes no space.
        border = EmptyBorder(0, 0, 8, 0)
        isVisible = false
        addItem(this)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = update()
            override fun removeUpdate(e: DocumentEvent) = update()
            override fun changedUpdate(e: DocumentEvent) = update()
            private fun update() {
                isVisible = text.isNotEmpty()
                this@SectionPage.revalidate()
                this@SectionPage.repaint()
            }
        })
    }

    /** A row with a fixed-width label on the left and a value on the right. The value can be selected and copied. */
    fun valueRow(label: String, value: String): JTextArea {
        val valueArea = WrappingText(value, reservedWidth = VALUE_LABEL_WIDTH).apply {
            wrapStyleWord = false
            foreground = UiStyles.FG_PRIMARY
        }
        val row = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(JLabel(label).apply {
                foreground = UiStyles.FG_SECONDARY
                verticalAlignment = SwingConstants.TOP
                alignmentY = Component.TOP_ALIGNMENT
                preferredSize = Dimension(VALUE_LABEL_WIDTH, preferredSize.height)
                minimumSize = preferredSize
                maximumSize = preferredSize
            })
            add(valueArea.apply { alignmentY = Component.TOP_ALIGNMENT })
        }
        addItem(row, gapAfter = 6)
        return valueArea
    }

    fun buttonRow(vararg buttons: JComponent): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        buttons.forEachIndexed { index, button ->
            if (index > 0) add(Box.createHorizontalStrut(8))
            add(button)
        }
    }.also { addItem(it, gapAfter = 8) }

    fun addItem(component: JComponent, gapAfter: Int = 0) {
        component.alignmentX = Component.LEFT_ALIGNMENT
        if (component !is AbstractButton && component !is WrappingText) {
            component.maximumSize = Dimension(Int.MAX_VALUE, component.maximumSize.height)
        }
        add(component)
        if (gapAfter > 0) add(Box.createVerticalStrut(gapAfter))
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        val widthChanged = width != this.width
        super.setBounds(x, y, width, height)
        // The height of the wrapped text depends on the width, so the scroll pane must get the new preferred height.
        if (widthChanged) {
            invalidate()
            SwingUtilities.invokeLater { revalidate() }
        }
    }

    /** Text that wraps to the width of the page. [reservedWidth] is the width of the items on the left of the text. */
    private inner class WrappingText(text: String, private val reservedWidth: Int) : JTextArea(text) {
        init {
            isEditable = false
            isOpaque = false
            lineWrap = true
            border = EmptyBorder(0, 0, 0, 0)
        }

        override fun getPreferredSize(): Dimension {
            val page = this@SectionPage
            val available = page.width - page.insets.left - page.insets.right - reservedWidth
            if (available <= 0) return super.getPreferredSize()
            val view = getUI().getRootView(this)
            view.setSize(available.toFloat(), Float.MAX_VALUE)
            val height = view.getPreferredSpan(View.Y_AXIS).toInt() + insets.top + insets.bottom
            return Dimension(available, height)
        }

        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false

    companion object {
        private const val VALUE_LABEL_WIDTH = 110

        fun secondaryButton(text: String, onClick: () -> Unit): JButton = JButton(text).apply {
            UiStyles.styleSecondary(this)
            addActionListener { onClick() }
        }
    }
}
