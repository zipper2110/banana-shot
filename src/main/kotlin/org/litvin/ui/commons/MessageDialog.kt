package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.geom.Ellipse2D
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.math.max

/** The kind of a message. It sets the icon and its color. */
enum class MessageKind { INFO, HINT, WARNING, ERROR, QUESTION, DANGER }

/** A button of a message box. */
internal data class MessageButton(val label: String, val kind: UiButton.Kind = UiButton.Kind.SECONDARY)

/**
 * The message box of the app, in place of JOptionPane: the `.msg` element of design/dialogs-redesign/messages.html.
 * A round icon tells the kind of message. The title is in the box and in the window title bar.
 *
 * The message text has simple marks: a blank line starts a new paragraph, "**text**" is bold,
 * and a paragraph that is only a file path shows in a box with a monospace font.
 */
internal object MessageDialog {
    private val OK = listOf(MessageButton("OK", UiButton.Kind.LIME))

    /** Shows a message with an OK button and waits until the user closes it. */
    fun show(parent: Component?, kind: MessageKind, title: String, message: String, width: Int = widthFor(message)) {
        show(parent, kind, title, message, OK, defaultIndex = 0, cancelIndex = 0, width = width)
    }

    /**
     * Asks the user to confirm an action. Returns true after [confirmLabel].
     * For a [destructive] action, the button is red, and the safe button has the focus, so Enter does not do the action.
     */
    fun confirm(
        parent: Component?,
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String = "Cancel",
        destructive: Boolean = false,
        glyph: Ikon? = null,
    ): Boolean {
        val buttons = listOf(
            MessageButton(cancelLabel),
            MessageButton(confirmLabel, if (destructive) UiButton.Kind.DANGER else UiButton.Kind.LIME),
        )
        val kind = if (destructive) MessageKind.DANGER else MessageKind.QUESTION
        return show(parent, kind, title, message, buttons, defaultIndex = if (destructive) 0 else 1, cancelIndex = 0, glyph = glyph) == 1
    }

    /**
     * Shows the message with [buttons] and returns the index of the clicked button.
     * Enter clicks the button at [defaultIndex]. Escape and the close button of the window return [cancelIndex].
     */
    fun show(
        parent: Component?,
        kind: MessageKind,
        title: String,
        message: String,
        buttons: List<MessageButton>,
        defaultIndex: Int,
        cancelIndex: Int,
        glyph: Ikon? = null,
        width: Int = widthFor(message),
    ): Int {
        var result = cancelIndex
        val dialog = build(parent, kind, title, message, buttons, defaultIndex, glyph, width) { result = it }
        dialog.setLocationRelativeTo(parent?.let { SwingUtilities.getWindowAncestor(it) ?: it })
        dialog.isVisible = true
        return result
    }

    /** Builds the packed message box without showing it. [onClick] gets the index of the clicked button. */
    internal fun build(
        parent: Component?,
        kind: MessageKind,
        title: String,
        message: String,
        buttons: List<MessageButton>,
        defaultIndex: Int,
        glyph: Ikon? = null,
        width: Int = widthFor(message),
        onClick: (Int) -> Unit,
    ): JDialog {
        val dialog = DialogKit.modal(parent, title)
        dialog.name = "message-dialog"
        val swingButtons = buttons.mapIndexed { index, spec ->
            UiButton(spec.label, kind = spec.kind).apply {
                name = "message-button-$index"
                addActionListener {
                    onClick(index)
                    dialog.dispose()
                }
            }
        }
        dialog.contentPane = content(kind, title, bodyParts(message, textWidth(width)), DialogKit.footer(right = swingButtons), width, glyph)
        dialog.rootPane.defaultButton = swingButtons.getOrNull(defaultIndex)
        dialog.isResizable = false
        dialog.pack()
        SwingUtilities.invokeLater { swingButtons.getOrNull(defaultIndex)?.requestFocusInWindow() }
        return dialog
    }

    /**
     * The content of a message box: the icon on the left, the title and the [parts] on the right, and the [footer].
     * Other message-like dialogs, for example the analytics consent, use it with their own parts.
     */
    fun content(
        kind: MessageKind,
        title: String,
        parts: List<JComponent>,
        footer: JComponent,
        dialogWidth: Int = DialogKit.SMALL,
        glyph: Ikon? = null,
    ): JPanel {
        val text = Stack(pad = Insets(4, 0, 0, 0)).apply {
            add(WrapText(title, UiKit.font(15f, UiKit.Weight.SEMIBOLD), Palette.FG, 1.35f, textWidth(dialogWidth)), gapAfter = if (parts.isEmpty()) 0 else 8)
            parts.forEachIndexed { index, part -> add(part, gapAfter = if (index == parts.lastIndex) 0 else 8) }
        }
        val body = MessageBody(text, kind, glyph, dialogWidth)
        return DialogKit.content(dialogWidth, null, body, footer)
    }

    /** The icon on the left and the text column on the right. */
    private class MessageBody(
        private val text: Stack,
        private val kind: MessageKind,
        private val glyph: Ikon?,
        private val dialogWidth: Int,
    ) : JPanel(null), HeightForWidth {
        init {
            isOpaque = false
            add(text)
        }

        override fun heightForWidth(width: Int) = PAD_TOP + max(ICON, text.heightForWidth(width - TEXT_X - DialogKit.PAD_X)) + PAD_BOTTOM
        override fun getPreferredSize() = Dimension(dialogWidth, heightForWidth(if (width > 0) width else dialogWidth))
        override fun doLayout() {
            val w = width - TEXT_X - DialogKit.PAD_X
            text.setBounds(TEXT_X, PAD_TOP, w, text.heightForWidth(w))
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val (fg, tint) = colors(kind)
                g2.color = tint
                g2.fill(Ellipse2D.Double(DialogKit.PAD_X.toDouble(), PAD_TOP.toDouble(), ICON.toDouble(), ICON.toDouble()))
                val icon = UiKit.icon(glyph ?: iconOf(kind), 19, fg)
                icon.paintIcon(this, g2, DialogKit.PAD_X + (ICON - icon.iconWidth) / 2, PAD_TOP + (ICON - icon.iconHeight) / 2)
            } finally {
                g2.dispose()
            }
        }
    }

    /** A paragraph of the message box: gray text with 1.55 line height. */
    fun paragraph(text: String, width: Int = DialogKit.SMALL, color: Color = Palette.FG_2): WrapText =
        WrapText(runsOf(text, color), 1.55f, textWidth(width))

    /** The parts of [message]: paragraphs and path boxes. */
    fun bodyParts(message: String, wrapWidth: Int): List<JComponent> = buildList {
        message.trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.forEach { block ->
            // A path line in a block gets its own box. The other lines stay together as one paragraph.
            val text = mutableListOf<String>()
            fun flushText() {
                if (text.isNotEmpty()) add(WrapText(runsOf(text.joinToString("\n"), Palette.FG_2), 1.55f, wrapWidth))
                text.clear()
            }
            block.trim().lines().forEach { line ->
                if (isPath(line.trim())) {
                    flushText()
                    add(PathBox(line.trim()))
                } else {
                    text += line.trim()
                }
            }
            flushText()
        }
    }

    /** The text runs of [text]: the parts between "**" marks are bold and bright. */
    fun runsOf(text: String, color: Color): List<TextRun> = text.split("**").mapIndexedNotNull { index, part ->
        if (part.isEmpty()) null
        else if (index % 2 == 1) TextRun(part, UiKit.font(13f, UiKit.Weight.SEMIBOLD), Palette.FG)
        else TextRun(part, UiKit.font(13f), color)
    }

    private fun isPath(text: String) =
        !text.contains('\n') && (Regex("^[A-Za-z]:[\\\\/].*").matches(text) || text.startsWith("\\\\") || (text.startsWith("/") && !text.contains(' ')))

    private fun widthFor(message: String) = if (message.length > 220) DialogKit.WIDE else DialogKit.SMALL

    private fun textWidth(width: Int) = width - TEXT_X - DialogKit.PAD_X

    private fun iconOf(kind: MessageKind): Ikon = when (kind) {
        MessageKind.INFO -> Material2AL.INFO
        MessageKind.HINT -> Material2AL.LIGHTBULB
        MessageKind.WARNING -> Material2MZ.WARNING
        MessageKind.ERROR -> Material2AL.ERROR
        MessageKind.QUESTION -> Material2AL.HELP
        MessageKind.DANGER -> Material2AL.DELETE
    }

    private fun colors(kind: MessageKind): Pair<Color, Color> = when (kind) {
        MessageKind.INFO -> Palette.BLUE to Palette.BLUE_TINT
        MessageKind.HINT -> Palette.LIME to Palette.LIME_TINT
        MessageKind.WARNING -> Palette.YELLOW to Palette.YELLOW_TINT
        MessageKind.ERROR, MessageKind.DANGER -> Palette.RED to Palette.RED_TINT
        MessageKind.QUESTION -> Palette.FG to Palette.HIGHLIGHT
    }

    private const val ICON = 30
    private const val TEXT_X = DialogKit.PAD_X + ICON + 14
    private const val PAD_TOP = 20
    private const val PAD_BOTTOM = 16
}

/** A file path in a box with a monospace font. The user can select and copy it. A long path wraps at any character. */
internal class PathBox(path: String) : JTextArea(path) {
    init {
        isEditable = false
        lineWrap = true
        wrapStyleWord = false
        isOpaque = false
        font = MonoFont.of(12f)
        foreground = Palette.FG
        caretColor = Palette.FG
        selectionColor = Palette.SELECTION
        selectedTextColor = Palette.FG
        border = BorderFactory.createEmptyBorder(7, 10, 7, 10)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.INSET, Palette.LINE)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
