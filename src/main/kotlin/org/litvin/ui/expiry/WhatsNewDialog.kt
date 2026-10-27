package org.litvin.ui.expiry

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.license.update.WhatsNewSeen
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Insets
import java.awt.geom.RoundRectangle2D
import java.util.concurrent.Executor
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import kotlin.math.ceil
import kotlin.math.min

/** What the "What's new" popup needs (B-54). Null in [ExpiryUi] shows no popup. */
internal class WhatsNewSupport(
    val seen: WhatsNewSeen,
    /** Gives the HTML of the Markdown page at the address, or null. It blocks, so it runs on [background]. */
    val load: (markdownUrl: String, maxImageWidth: Int) -> String?,
    val background: Executor,
)

/**
 * The "What's new" popup (B-54): the title, the page of the release, the link to the site, and the update buttons.
 * The user can close it at any time. One dialog object stays for the session.
 */
internal class WhatsNewDialog(
    private val parent: Component,
    updateButtons: UpdateButtons,
    private val openUrl: (String) -> Unit,
) {
    private var pageUrl: String? = null

    val page: JEditorPane = object : JEditorPane() {
        // The page wraps at the width of the scroll pane.
        override fun getScrollableTracksViewportWidth() = true
    }.apply {
        name = "whats-new-page"
        isEditable = false
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = UiKit.font(13f)
        foreground = Palette.FG_2
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val target = event.url?.toString() ?: event.description
                if (target != null && LINK_PREFIXES.any { target.startsWith(it, ignoreCase = true) }) openUrl(target)
            }
        }
    }

    private val scroll = JScrollPane(page).apply {
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
        viewport.isOpaque = false
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
        runCatching { applyDarkScrollbar(this) }
    }

    /** Holds the scroll pane with the height of the page, up to [MAX_PAGE_HEIGHT]. */
    private val pageBox = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(scroll, BorderLayout.CENTER)
    }

    val readMore = SiteLink(ExpiryTexts.READ_MORE) { pageUrl?.let(openUrl) }.apply { name = "whats-new-read-more" }
    private val readMoreRow = SiteLink.row(readMore)

    val later = UiButton(ExpiryTexts.LATER).apply {
        name = "whats-new-later"
        addActionListener { this@WhatsNewDialog.hide() }
    }

    val dialog: JDialog = DialogKit.modal(parent, ExpiryTexts.WHATS_NEW_TITLE).apply {
        name = "whats-new-dialog"
        defaultCloseOperation = JDialog.HIDE_ON_CLOSE
        DialogKit.onEscape(this) { this@WhatsNewDialog.hide() }
        val body = Stack(pad = Insets(4, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 12).apply {
            add(pageBox)
            add(readMoreRow)
        }
        contentPane = DialogKit.content(
            WIDTH,
            head(),
            body,
            DialogKit.footer(right = listOf(later, updateButtons.downloadUpdate, updateButtons.updateAndRestart)),
        )
        isResizable = false
    }

    /** Shows [html]. The call returns when the popup closes, because the popup is modal. */
    fun show(html: String, pageUrl: String?) {
        this.pageUrl = pageUrl
        readMoreRow.isVisible = pageUrl != null
        page.editorKit = editorKit()
        page.text = "<html><body>$html</body></html>"
        page.caretPosition = 0
        val inner = WIDTH - DialogKit.PAD_X * 2
        page.size = Dimension(inner - SCROLLBAR, Short.MAX_VALUE.toInt())
        val height = min(page.preferredSize.height, MAX_PAGE_HEIGHT)
        pageBox.preferredSize = Dimension(inner, height)
        dialog.pack()
        DialogKit.packToScreen(dialog)
        dialog.setLocationRelativeTo(parent)
        if (!dialog.isVisible) dialog.isVisible = true
    }

    fun hide() {
        if (dialog.isVisible) dialog.isVisible = false
    }

    /** The icon and the title. */
    private fun head(): JComponent = JPanel(BorderLayout(12, 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, DialogKit.PAD_X, 10, DialogKit.PAD_X)
        add(IconBadge(), BorderLayout.WEST)
        add(JLabel(ExpiryTexts.WHATS_NEW_TITLE).apply {
            name = "whats-new-title"
            font = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
            foreground = Palette.FG
        }, BorderLayout.CENTER)
    }

    /** A new style sheet for each page. A change to the shared sheet of [HTMLEditorKit] would change all HTML texts. */
    private fun editorKit(): HTMLEditorKit {
        val sheet = StyleSheet().apply {
            addStyleSheet(HTMLEditorKit().styleSheet)
            val fg = Palette.hex(Palette.FG)
            val muted = Palette.hex(Palette.FG_3)
            addRule("body { margin: 0; color: ${Palette.hex(Palette.FG_2)}; }")
            addRule("p { margin-top: 0; margin-bottom: 8px; }")
            addRule("h1 { font-size: 18pt; color: $fg; margin-top: 4px; margin-bottom: 6px; }")
            addRule("h2 { font-size: 15pt; color: $fg; margin-top: 10px; margin-bottom: 6px; }")
            addRule("h3, h4, h5, h6 { font-size: 13pt; color: $fg; margin-top: 8px; margin-bottom: 4px; }")
            addRule("a { color: ${Palette.hex(Palette.BLUE)}; }")
            addRule("ul, ol { margin-top: 0; margin-bottom: 8px; margin-left: 18px; }")
            addRule("li { margin-bottom: 3px; }")
            addRule("blockquote { color: $muted; margin-left: 12px; margin-top: 0; margin-bottom: 8px; }")
            addRule("code, pre { font-family: monospaced; color: $fg; }")
            addRule("th { color: $fg; text-align: left; }")
            addRule("td, th { padding: 3px 10px 3px 0; }")
        }
        return object : HTMLEditorKit() {
            override fun getStyleSheet() = sheet
        }
    }

    /** The light bulb on a lime square, as the update notice. */
    private class IconBadge : JComponent() {
        override fun getPreferredSize() = Dimension(SIZE, SIZE)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val y = (height - SIZE) / 2
                g2.color = Palette.LIME_TINT
                g2.fill(RoundRectangle2D.Float(0f, y.toFloat(), SIZE.toFloat(), SIZE.toFloat(), 8f, 8f))
                val icon = UiKit.icon(Material2AL.LIGHTBULB, 18, Palette.LIME)
                icon.paintIcon(this, g2, (SIZE - icon.iconWidth) / 2, y + (SIZE - icon.iconHeight) / 2)
            } finally {
                g2.dispose()
            }
        }

        companion object {
            const val SIZE = 30
        }
    }

    companion object {
        const val WIDTH = 600
        const val MAX_PAGE_HEIGHT = 440
        private const val SCROLLBAR = 14

        /** The widest image on the page. A wider image gets smaller. */
        const val IMAGE_WIDTH = WIDTH - DialogKit.PAD_X * 2 - SCROLLBAR

        private val LINK_PREFIXES = listOf("https:", "http:", "mailto:")
    }
}

/** A text link with the "open in new" icon. It opens a page in the browser. */
internal class SiteLink(text: String, onOpen: () -> Unit) : JButton(text) {
    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = UiKit.font(12.5f)
        addActionListener { onOpen() }
    }

    private val textWidth get() = ceil(UiKit.textWidth(text, font)).toInt()

    override fun getPreferredSize() = Dimension(textWidth + GAP + ICON + 2, HEIGHT_PX)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val active = model.isRollover || isFocusOwner
            val color = if (active) Palette.FG else Palette.BLUE
            UiKit.drawText(g2, text, font, color, 0f, 0f, height.toFloat())
            if (active) {
                val baseline = (height + g2.getFontMetrics(font).ascent) / 2 + 2
                g2.fillRect(0, baseline, textWidth, 1)
            }
            val icon = UiKit.icon(Material2MZ.OPEN_IN_NEW, ICON, color)
            icon.paintIcon(this, g2, textWidth + GAP, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val GAP = 4
        private const val ICON = 14
        private const val HEIGHT_PX = 22

        /** A row that keeps the link at its own width, so only the text and the icon can be clicked. */
        fun row(link: SiteLink): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(link)
        }
    }
}
