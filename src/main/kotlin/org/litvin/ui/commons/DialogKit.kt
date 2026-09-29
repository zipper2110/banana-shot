package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.JViewport
import java.awt.Rectangle
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The parts of all dialogs: the frame (head, body, foot), form fields, group cards and the footer with buttons.
 * The look comes from design/dialogs-redesign (shared.css, "Dialog kit"). Each dialog has the same structure:
 * the head with the title, the body, and the foot. The foot has Reset or Delete on the left, then Cancel and the main action on the right.
 */
internal object DialogKit {
    val DIALOG_BG = Color(0x1C1C1C)
    val FOOTER_BG = Color(0x181818)
    val INPUT_BG = Color(0x121212)

    /** The width of a small dialog, for example a message or Rename project. */
    const val SMALL = 420

    /** The width of a normal dialog, for example New project. */
    const val MEDIUM = 480

    /** The width of a message with a long text. */
    const val WIDE = 540

    /** The side padding of the head, the body and the foot. */
    const val PAD_X = 20

    /** A dialog over the window of [parent]. Escape closes it. */
    fun modal(
        parent: Component?,
        title: String,
        modality: Dialog.ModalityType = Dialog.ModalityType.APPLICATION_MODAL,
    ): JDialog {
        val owner = parent as? Window ?: parent?.let { SwingUtilities.getWindowAncestor(it) }
        return JDialog(owner, title, modality).apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            onEscape(this) { dispose() }
        }
    }

    /** Runs [action] when the user presses Escape in [dialog]. */
    fun onEscape(dialog: JDialog, action: () -> Unit) {
        dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "dialog-escape")
        dialog.rootPane.actionMap.put("dialog-escape", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = action()
        })
    }

    /** The content of a dialog of [width] pixels: the head, the body, and the foot, on the dark dialog background. */
    fun content(width: Int, head: JComponent?, body: JComponent, footer: JComponent?): JPanel =
        Stack(background = DIALOG_BG, fixedWidth = width).apply {
            head?.let { add(it) }
            add(body, fill = true)
            footer?.let { add(it) }
        }

    /** The title of the dialog. [sub] is a short value on the right, for example the length of a point. */
    fun head(title: String, sub: String? = null): JComponent = JPanel(BorderLayout(10, 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, PAD_X, 6, PAD_X)
        add(JLabel(title).apply {
            font = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
            foreground = UiKit.FG
        }, BorderLayout.WEST)
        if (sub != null) add(JLabel(sub).apply {
            font = UiKit.font(12f)
            foreground = UiKit.FG_3
        }, BorderLayout.EAST)
    }

    /** The title with a line of text under it, for a dialog that must tell how it works. */
    fun head(title: String, line: String, dialogWidth: Int): JComponent = Stack(pad = Insets(16, PAD_X, 4, PAD_X), gap = 2).apply {
        add(JLabel(title).apply {
            font = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
            foreground = UiKit.FG
        })
        add(WrapText(line, UiKit.font(12f), UiKit.FG_3, 1.45f, dialogWidth - PAD_X * 2))
    }

    /** The body: the rows one under the other with [gap] pixels between them. */
    fun form(vararg rows: JComponent, gap: Int = 12): JComponent =
        Stack(pad = Insets(10, PAD_X, 12, PAD_X), gap = gap).apply { rows.forEach { add(it) } }

    /** Two fields side by side. */
    fun pair(left: JComponent, right: JComponent): JComponent = JPanel(GridLayout(1, 2, 12, 0)).apply {
        isOpaque = false
        add(left)
        add(right)
    }

    /** A caption above an input, with an optional small note such as "optional". */
    fun field(caption: String, input: JComponent, note: String? = null): JComponent = JPanel(BorderLayout(0, 5)).apply {
        isOpaque = false
        add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            (layout as FlowLayout).alignOnBaseline = true
            add(JLabel(caption).apply {
                font = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
                foreground = UiKit.FG_2
            })
            if (note != null) add(JLabel("  $note").apply {
                font = UiKit.font(11f)
                foreground = UiKit.FG_3
                // Some slack, because the painted text can be a little wider than the measured text.
                preferredSize = Dimension(preferredSize.width + 4, preferredSize.height)
            })
        }, BorderLayout.NORTH)
        add(input, BorderLayout.CENTER)
    }

    /**
     * A dark input box around [input]. The box paints the rounded background and the border.
     * The border is lime while the input has the focus, and red while [hasError] returns true.
     * [boxHeight] is null for a box that follows the input, for example a text area.
     */
    fun inputBox(
        input: JTextComponent,
        boxHeight: Int? = INPUT_HEIGHT,
        padding: Int = 0,
        hasError: () -> Boolean = { false },
    ): JComponent {
        styleInput(input)
        input.border = BorderFactory.createEmptyBorder(padding, 10, padding, 10)
        val box = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = UiKit.smooth(g)
                try {
                    val border = when {
                        hasError() -> ERROR_LINE
                        input.isFocusOwner -> UiKit.LIME_LINE
                        else -> UiKit.LINE_2
                    }
                    val alpha = if (input.isEnabled) 1f else 0.4f
                    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
                    UiKit.paintBox(g2, 0, 0, width, height, 4, INPUT_BG, border)
                } finally {
                    g2.dispose()
                }
            }
        }
        box.isOpaque = false
        box.border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        if (input is JTextArea) {
            // A long text scrolls inside the box.
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
            box.maximumSize = Dimension(Int.MAX_VALUE, boxHeight)
        }
        input.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = box.repaint()
            override fun focusLost(e: FocusEvent) = box.repaint()
        })
        return box
    }

    /** The colors and the font of a text input on the dark field background. */
    fun styleInput(input: JTextComponent) {
        input.background = INPUT_BG
        input.foreground = UiKit.FG
        input.caretColor = UiKit.FG
        input.selectionColor = Color(161, 254, 0, 70)
        input.selectedTextColor = UiKit.FG
        input.disabledTextColor = UiKit.FG_3
        input.font = UiKit.font(13f)
        input.isOpaque = false
    }

    /** A combo box with the look of a dark input: the `select.inp` element of the design. */
    fun <T> styleCombo(combo: JComboBox<T>) {
        combo.font = UiKit.font(13f)
        combo.background = INPUT_BG
        combo.foreground = UiKit.FG
        combo.putClientProperty(
            "FlatLaf.style",
            "borderColor: #363636; focusedBorderColor: #A1FE0099; arc: 8; buttonStyle: none;" +
                " buttonArrowColor: #ADAAAA; popupBackground: #1C1C1C; padding: 4,10,4,6",
        )
        combo.preferredSize = Dimension(combo.preferredSize.width, INPUT_HEIGHT)
    }

    /** The red line for a form error. It keeps its height when it is empty, so the dialog does not move. */
    fun errorLine(componentName: String? = null): JLabel = JLabel(" ").apply {
        name = componentName
        font = UiKit.font(12f)
        foreground = UiKit.ERROR
        preferredSize = Dimension(0, 16)
        maximumSize = Dimension(Int.MAX_VALUE, 16)
    }

    /**
     * The foot: [left] items, a flexible gap, then [right] items, with 8 px between two items.
     * A [gap] item adds more space, for example between "Reset to defaults" and Cancel.
     */
    fun footer(left: List<JComponent> = emptyList(), right: List<JComponent>): JComponent = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            g.color = FOOTER_BG
            g.fillRect(0, 0, width, height)
            g.color = UiKit.LINE
            g.fillRect(0, 0, width, 1)
        }
    }.apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(12, PAD_X, 12, PAD_X)
        left.forEachIndexed { index, item ->
            if (index > 0) add(Box.createHorizontalStrut(8))
            add(item)
        }
        add(Box.createHorizontalGlue())
        right.forEachIndexed { index, item ->
            if (index > 0 && item !is Box.Filler && right[index - 1] !is Box.Filler) add(Box.createHorizontalStrut(8))
            add(item)
        }
    }

    /** Extra space between two footer buttons. */
    fun gap(width: Int): JComponent = Box.createHorizontalStrut(width) as JComponent

    /** The key help of the foot, for example "[Enter] save · [Esc] cancel". */
    fun keysHint(vararg keys: Pair<String, String>): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        val style = KeyChipStyle(fill = UiKit.RAISED_2, border = Color(0x3A3A3A), text = UiKit.FG_2, height = 18)
        fun text(value: String) = JLabel(value).apply {
            font = UiKit.font(11f)
            foreground = UiKit.FG_3
            // Some slack, because the painted text can be a little wider than the measured text.
            preferredSize = Dimension(ceil(UiKit.textWidth(value, font)).toInt() + 4, preferredSize.height)
        }
        keys.forEachIndexed { index, (key, action) ->
            if (index > 0) add(text(" · "))
            add(KeyChip(key, style))
            add(text(" $action"))
        }
        maximumSize = preferredSize
    }

    /** Wraps [body] in a borderless scroll pane with a dark scroll bar, for a dialog that can be taller than the screen. */
    fun scrollBody(body: JComponent, background: Color = DIALOG_BG): JScrollPane = JScrollPane(ScrollColumn(body)).apply {
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
        viewport.isOpaque = true
        viewport.background = background
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
        runCatching { applyDarkScrollbar(this, background) }
    }

    /** Packs [dialog] and makes it not taller than the screen. A scroll pane in the body gets the rest of the height. */
    fun packToScreen(dialog: JDialog) {
        dialog.pack()
        val screen = runCatching {
            val config = dialog.graphicsConfiguration ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val insets = dialog.toolkit.getScreenInsets(config)
            config.bounds.height - insets.top - insets.bottom - 40
        }.getOrNull() ?: return
        if (dialog.height > screen) dialog.setSize(dialog.width, max(300, screen))
    }

    const val INPUT_HEIGHT = 34
    private val ERROR_LINE = Color(255, 115, 81, 179)
}

/**
 * A column of components with the full width of the column. The height of a child follows its width
 * (see [heightAtWidth]), so wrapped text gets the correct height. [fixedWidth] gives the column a preferred width.
 * A child added with fill = true gets the rest of the height when the column is taller than its content.
 */
internal open class Stack(
    private val pad: Insets = Insets(0, 0, 0, 0),
    private val gap: Int = 0,
    background: Color? = null,
    private val fixedWidth: Int? = null,
) : JPanel(null), HeightForWidth {
    private val gapsAfter = HashMap<Component, Int>()
    private var filler: Component? = null

    init {
        isOpaque = background != null
        if (background != null) this.background = background
    }

    /** Adds [component]. [gapAfter] replaces the normal gap after it. */
    fun add(component: JComponent, gapAfter: Int? = null, fill: Boolean = false): JComponent {
        super.add(component)
        if (gapAfter != null) gapsAfter[component] = gapAfter
        if (fill) filler = component
        return component
    }

    private val shown get() = components.filter { it.isVisible }

    private fun gapAfter(child: Component, last: Boolean) = if (last) 0 else gapsAfter[child] ?: gap

    override fun heightForWidth(width: Int): Int {
        val inner = width - pad.left - pad.right
        val children = shown
        return pad.top + pad.bottom + children.withIndex().sumOf { (i, child) ->
            heightOf(child, inner) + gapAfter(child, i == children.lastIndex)
        }
    }

    private fun heightOf(child: Component, width: Int): Int =
        if (child is JScrollPane) {
            val view = child.viewport.view
            val insets = child.insets
            (if (view != null) heightAtWidth(view, width - insets.left - insets.right) else 0) + insets.top + insets.bottom
        } else {
            heightAtWidth(child, width)
        }

    private fun preferredWidth(): Int =
        fixedWidth ?: (pad.left + pad.right + (shown.maxOfOrNull { it.preferredSize.width } ?: 0))

    override fun getPreferredSize(): Dimension {
        val w = fixedWidth ?: if (width > 0) width else preferredWidth()
        return Dimension(preferredWidth(), heightForWidth(w))
    }

    override fun getMinimumSize(): Dimension = Dimension(min(preferredWidth(), 200), 0)

    override fun doLayout() {
        val inner = width - pad.left - pad.right
        val children = shown
        val heights = children.map { heightOf(it, inner) }
        val content = heights.sum() + children.withIndex().sumOf { (i, c) -> gapAfter(c, i == children.lastIndex) } + pad.top + pad.bottom
        val extra = height - content
        var y = pad.top
        children.forEachIndexed { index, child ->
            var h = heights[index]
            if (child === filler) h = max(0, h + extra)
            child.setBounds(pad.left, y, inner, h)
            y += h + gapAfter(child, index == children.lastIndex)
        }
    }
}

/**
 * A card with an uppercase caption and an icon, and rows under it: the `.group` element of the dialog design.
 * It has the look of [GroupCard] in the side panels, but its rows can have different heights.
 * [labelWidth] is the width of the row labels.
 */
internal class DialogGroup(
    title: String,
    ikon: Ikon,
    componentName: String? = null,
    private val labelWidth: Int = LABEL_WIDTH,
) : Stack(pad = Insets(1, 1, 5, 1)) {
    private val caption = JPanel(BorderLayout(6, 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(8, 14, 4, 14)
    }
    private val captionLabel = object : JLabel(title.uppercase(), UiKit.icon(ikon, 14, UiKit.FG_3), LEFT) {
        // The label measures the text without the letter spacing, so it adds the width of the spacing.
        override fun getPreferredSize(): Dimension = super.getPreferredSize().let {
            Dimension(icon.iconWidth + iconTextGap + ceil(UiKit.textWidth(text, font)).toInt() + 4, it.height)
        }
    }.apply {
        font = UiKit.trackedFont(10.5f, 0.1, UiKit.Weight.BOLD)
        foreground = UiKit.FG_3
        iconTextGap = 6
    }

    /** A value next to the caption, after a short line, for example the name of the selected style. */
    val note = JLabel().apply {
        font = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
        foreground = UiKit.FG_2
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 4, 0, 0),
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, UiKit.LINE_2),
                BorderFactory.createEmptyBorder(0, 8, 0, 0),
            ),
        )
        isVisible = false
    }

    /** A short text at the right end of the caption, for example "Not used in manual scoring". */
    val aside = object : JLabel() {
        // Some slack, because the painted text can be a little wider than the measured text.
        override fun getPreferredSize(): Dimension = super.getPreferredSize().let {
            Dimension(if (text.isNullOrEmpty()) 0 else ceil(UiKit.textWidth(text, font)).toInt() + 6, it.height)
        }
    }.apply {
        font = UiKit.font(11.5f)
        foreground = UiKit.FG_3
    }

    init {
        name = componentName
        caption.add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            (layout as FlowLayout).alignOnBaseline = true
            add(captionLabel)
            add(note)
        }, BorderLayout.WEST)
        caption.add(aside, BorderLayout.EAST)
        add(caption)
    }

    /** Shows [text] next to the caption. An empty text hides the note. */
    fun showNote(text: String) {
        note.text = text
        note.isVisible = text.isNotEmpty()
    }

    private var lastWasRow = false

    /** A row with [label] on the left and [control] on the right. Returns the label, so the caller can dim it. */
    fun row(label: String, control: JComponent, line: Boolean = lastWasRow): JLabel {
        val rowLabel = JLabel(label).apply {
            font = UiKit.font(12.5f)
            foreground = UiKit.FG_2
        }
        add(GroupRow(rowLabel, control, line, labelWidth))
        lastWasRow = true
        return rowLabel
    }

    /** A row with one component over the full width, for example a switch. */
    fun wide(component: JComponent, line: Boolean = lastWasRow) {
        add(GroupRow(null, component, line, labelWidth))
        lastWasRow = true
    }

    /** A text under a row. [indent] aligns it with the controls. */
    fun text(text: WrapText, indent: Boolean = false, lineAbove: Boolean = false, top: Int = 0) {
        add(Indented(text, if (indent) labelWidth + 14 + LABEL_GAP else 14, lineAbove, top, 10))
        lastWasRow = false
    }

    /** A component under a row, [left] pixels from the edge, for example the title field under the Title switch. */
    fun indented(component: JComponent, left: Int, bottom: Int = 10) {
        add(Indented(component, left, false, 0, bottom))
        lastWasRow = false
    }

    /** A row that lays out and paints itself, for example a [SliderValueRow] of the side panels. */
    fun custom(component: JComponent) {
        add(component)
        lastWasRow = true
    }

    /** A component that fills the rest of the card height, for example a scrolling list. */
    fun fill(component: JComponent) {
        add(component, fill = true)
        lastWasRow = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 8, UiKit.CARD, UiKit.LINE)
        } finally {
            g2.dispose()
        }
    }

    /** A label and a control. The row is at least 44 px high. */
    private class GroupRow(
        private val label: JLabel?,
        private val control: JComponent,
        private val line: Boolean,
        private val labelWidth: Int,
    ) : JPanel(null), HeightForWidth {
        init {
            isOpaque = false
            label?.let { add(it) }
            add(control)
        }

        private fun controlX() = if (label != null) 14 + labelWidth + LABEL_GAP else 14

        override fun heightForWidth(width: Int): Int = max(ROW_HEIGHT, heightAtWidth(control, width - controlX() - 14) + 14)
        override fun getPreferredSize() = Dimension(labelWidth + 200, heightForWidth(if (width > 0) width else labelWidth + 200))

        override fun doLayout() {
            val x = controlX()
            val controlWidth = width - x - 14
            val preferred = control.preferredSize
            val h = heightAtWidth(control, controlWidth)
            val w = if (control.maximumSize.width < controlWidth) min(preferred.width, controlWidth) else controlWidth
            control.setBounds(x, (height - h) / 2, w, h)
            label?.setBounds(14, 0, labelWidth, height)
        }

        override fun paintComponent(g: Graphics) {
            if (!line) return
            g.color = UiKit.CARD_ROW_LINE
            g.fillRect(0, 0, width, 1)
        }
    }

    /** A component with a left indent and space above and below it. */
    private class Indented(
        private val component: JComponent,
        private val left: Int,
        private val line: Boolean,
        private val top: Int,
        private val bottom: Int,
    ) : JPanel(null), HeightForWidth {
        init {
            isOpaque = false
            add(component)
        }

        private fun inner(width: Int) = width - left - 14

        override fun heightForWidth(width: Int) = top + heightAtWidth(component, inner(width)) + bottom
        override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 300))
        override fun doLayout() = component.setBounds(left, top, inner(width), heightAtWidth(component, inner(width)))

        override fun paintComponent(g: Graphics) {
            if (!line) return
            g.color = UiKit.CARD_ROW_LINE
            g.fillRect(0, 0, width, 1)
        }
    }

    companion object {
        const val LABEL_WIDTH = 118
        const val LABEL_GAP = 12
        const val ROW_HEIGHT = 44
    }
}

/**
 * The view of a dialog scroll pane. It follows the width of the viewport, and its height is the height of [content]
 * at that width, so a scroll bar shows only when the content is taller than the viewport.
 */
internal class ScrollColumn(private val content: JComponent) : JPanel(BorderLayout()), Scrollable, HeightForWidth {
    init {
        isOpaque = false
        add(content, BorderLayout.CENTER)
    }

    override fun heightForWidth(width: Int) = heightAtWidth(content, width)

    override fun getPreferredSize(): Dimension {
        val viewport = parent as? JViewport
        // Before the first layout the viewport has no width, but the scroll pane has its width already.
        val scroll = viewport?.parent as? JScrollPane
        val w = viewport?.width?.takeIf { it > 0 }
            ?: scroll?.let { it.width - it.insets.left - it.insets.right }?.takeIf { it > 0 }
            ?: content.preferredSize.width
        return Dimension(w, heightForWidth(w))
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = max(16, visibleRect.height - 16)
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

/** Sets [enabled] on [root] and on all components in it. */
internal fun setEnabledDeep(root: Component, enabled: Boolean) {
    root.isEnabled = enabled
    if (root is Container) root.components.forEach { setEnabledDeep(it, enabled) }
}
