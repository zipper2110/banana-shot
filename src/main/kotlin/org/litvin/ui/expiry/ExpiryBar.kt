package org.litvin.ui.expiry

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.license.update.UpdateOptions
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

/**
 * The bars at the top of the content area (E8 decision of 2026-10-03): the banner of expired mode and the notices.
 * None of them is modal. Hidden rows take no space.
 */
internal class ExpiryBar : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
        name = "expiry-bar"
        addComponentListener(object : ComponentAdapter() {
            // The height depends on the width. A new width can need a new height.
            override fun componentResized(e: ComponentEvent) {
                if (heightForWidth(width) != height) revalidate()
            }
        })
    }

    fun addRow(row: NoticeRow) {
        add(row)
    }

    private val shown get() = components.filter { it.isVisible }

    override fun heightForWidth(width: Int): Int = shown.sumOf { (it as HeightForWidth).heightForWidth(width) }

    override fun getPreferredSize(): Dimension {
        val w = if (width > 0) width else parent?.width?.takeIf { it > 0 } ?: 1000
        return Dimension(w, heightForWidth(w))
    }

    override fun doLayout() {
        var y = 0
        shown.forEach { row ->
            val h = (row as HeightForWidth).heightForWidth(width)
            row.setBounds(0, y, width, h)
            y += h
        }
    }
}

/**
 * One bar: an icon, a bold title, lines of plain text, the buttons, and a close button when the user can close it.
 * The texts from the rules file (`notes`, `message`) go in a [WrapText], which does not show HTML (E8-S8).
 */
internal class NoticeRow(
    private val rowName: String,
    private val kind: MessageKind,
    onClose: (() -> Unit)?,
) : JPanel(null), HeightForWidth {
    private val text = Stack(gap = 3)
    private val title = WrapText("", UiKit.font(13.5f, UiKit.Weight.SEMIBOLD), Palette.FG, 1.35f).apply { name = "$rowName-title" }
    private val lines = mutableListOf<WrapText>()
    private val buttons = JPanel(null).apply { isOpaque = false }
    private val closeButton = onClose?.let {
        UiButton("", Material2AL.CLOSE, UiButton.Kind.GHOST, buttonHeight = 28).apply {
            name = "$rowName-close"
            toolTipText = "Close"
            accessibleContext.accessibleName = "Close"
            addActionListener { it() }
        }
    }
    private var buttonList: List<JComponent> = emptyList()

    init {
        name = rowName
        isOpaque = false
        isVisible = false
        text.add(title)
        add(text)
        add(buttons)
        closeButton?.let { add(it) }
    }

    /** Sets the title and the lines. A null line does not show. */
    fun setTexts(titleText: String, lineTexts: List<String?>) {
        if (title.text != titleText) title.runs = listOf(TextRun(titleText, title.runs.first().font, Palette.FG))
        val shownLines = lineTexts.filterNotNull()
        while (lines.size < shownLines.size) {
            val line = WrapText("", UiKit.font(12.5f), Palette.FG_2, 1.45f).apply { name = "$rowName-line-${lines.size}" }
            lines += line
            text.add(line)
        }
        lines.forEachIndexed { index, line ->
            val value = shownLines.getOrNull(index)
            line.isVisible = value != null
            if (value != null && line.text != value) line.runs = listOf(TextRun(value, line.runs.first().font, Palette.FG_2))
        }
        revalidateBar()
    }

    fun setButtons(list: List<JComponent>) {
        buttonList = list
        buttons.removeAll()
        list.forEach { buttons.add(it) }
        revalidateBar()
    }

    /** The plain texts that show, for tests. */
    val shownText: String
        get() = (listOf(title) + lines.filter { it.isVisible }).joinToString("\n") { it.text }

    private fun revalidateBar() {
        revalidate()
        parent?.revalidate()
        repaint()
    }

    private fun buttonsWidth(): Int {
        val visible = buttonList.filter { it.isVisible }
        return visible.sumOf { it.preferredSize.width } + GAP * max(0, visible.size - 1)
    }

    private fun textWidth(width: Int, buttonsBelow: Boolean): Int {
        val right = PAD_X + (closeButton?.let { CLOSE + GAP } ?: 0) + if (buttonsBelow) 0 else buttonsWidth() + GAP * 2
        return max(120, width - TEXT_X - right)
    }

    /** The buttons go under the text when the text would be too narrow beside them. */
    private fun buttonsBelow(width: Int) = buttonsWidth() > 0 && width - TEXT_X - PAD_X - buttonsWidth() < MIN_TEXT

    override fun heightForWidth(width: Int): Int {
        val below = buttonsBelow(width)
        val textHeight = text.heightForWidth(textWidth(width, below))
        val buttonsHeight = if (buttonsWidth() > 0) BUTTON_HEIGHT else 0
        val content = if (below) textHeight + GAP + buttonsHeight else max(textHeight, buttonsHeight)
        return PAD_Y * 2 + max(content, ICON)
    }

    override fun getPreferredSize() = Dimension(if (width > 0) width else 1000, heightForWidth(if (width > 0) width else 1000))

    override fun doLayout() {
        val below = buttonsBelow(width)
        val textW = textWidth(width, below)
        val textH = text.heightForWidth(textW)
        text.setBounds(TEXT_X, PAD_Y, textW, textH)
        var x = 0
        buttonList.filter { it.isVisible }.forEach { button ->
            val size = button.preferredSize
            button.setBounds(x, (BUTTON_HEIGHT - size.height) / 2, size.width, size.height)
            x += size.width + GAP
        }
        val bw = buttonsWidth()
        closeButton?.setBounds(width - PAD_X - CLOSE, PAD_Y, CLOSE, CLOSE)
        if (below) {
            buttons.setBounds(TEXT_X, PAD_Y + textH + GAP, bw, BUTTON_HEIGHT)
        } else {
            val right = width - PAD_X - (closeButton?.let { CLOSE + GAP } ?: 0)
            buttons.setBounds(right - bw, PAD_Y + max(0, (textH - BUTTON_HEIGHT) / 2).coerceAtMost(4), bw, BUTTON_HEIGHT)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val (fg, tint) = colors(kind)
            g2.color = Palette.PANEL
            g2.fillRect(0, 0, width, height)
            g2.color = tint
            g2.fillRect(0, 0, width, height)
            g2.color = fg
            g2.fillRect(0, 0, STRIPE, height)
            g2.color = Palette.LINE
            g2.fillRect(0, height - 1, width, 1)
            val icon = UiKit.icon(iconOf(kind), ICON, fg)
            icon.paintIcon(this, g2, PAD_X, PAD_Y)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 16
        const val PAD_Y = 10
        const val ICON = 20
        const val TEXT_X = PAD_X + ICON + 12
        const val GAP = 8
        const val CLOSE = 28
        const val STRIPE = 3
        const val BUTTON_HEIGHT = 32
        const val MIN_TEXT = 320

        fun iconOf(kind: MessageKind): Ikon = when (kind) {
            MessageKind.ERROR, MessageKind.DANGER -> Material2AL.ERROR
            MessageKind.WARNING -> Material2MZ.WARNING
            MessageKind.HINT -> Material2AL.LIGHTBULB
            MessageKind.INFO, MessageKind.QUESTION -> Material2AL.INFO
        }

        fun colors(kind: MessageKind): Pair<Color, Color> = when (kind) {
            MessageKind.ERROR, MessageKind.DANGER -> Palette.RED to Palette.RED_TINT
            MessageKind.WARNING -> Palette.YELLOW to Palette.YELLOW_TINT
            MessageKind.HINT -> Palette.LIME to Palette.LIME_TINT
            MessageKind.INFO, MessageKind.QUESTION -> Palette.BLUE to Palette.BLUE_TINT
        }
    }
}

/**
 * The two buttons of "Update and restart" for one place (build-expiry-spec.md, "Update and restart"). All places
 * share one [UpdateRunner], so each "Update and restart" button shows the progress of the one download.
 */
internal class UpdateButtons(prefix: String, private val runner: UpdateRunner) {
    private var options: UpdateOptions? = null

    val updateAndRestart = UiButton(ExpiryTexts.UPDATE_AND_RESTART, kind = UiButton.Kind.LIME).apply {
        name = "$prefix-update-and-restart"
        addActionListener { options?.let { runner.updateAndRestart(it.installerUrl) } }
    }

    val downloadUpdate = UiButton(ExpiryTexts.DOWNLOAD_UPDATE).apply {
        name = "$prefix-download-update"
        addActionListener { options?.let { runner.openDownloadPage(it.downloadPageUrl) } }
    }

    val all: List<JComponent> get() = listOf(updateAndRestart, downloadUpdate)

    init {
        runner.addListener(::showProgress)
        showProgress()
    }

    fun show(value: UpdateOptions) {
        options = value
        updateAndRestart.isVisible = value.showsUpdateAndRestart
    }

    private fun showProgress() {
        val progress = runner.progress
        updateAndRestart.text = progress ?: ExpiryTexts.UPDATE_AND_RESTART
        updateAndRestart.isEnabled = progress == null
        updateAndRestart.revalidate()
        (updateAndRestart.parent?.parent as? Component)?.revalidate()
    }
}
