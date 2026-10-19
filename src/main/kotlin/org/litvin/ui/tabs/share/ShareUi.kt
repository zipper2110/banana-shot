package org.litvin.ui.tabs.share

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.litvin.share.FrameView
import org.litvin.share.ThumbnailLayout
import org.litvin.share.ThumbnailRenderer
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import org.litvin.i18n.tr

/** Fonts and small parts of the Share tab. The layout comes from design/share-redesign/final.html. */
internal object ShareUi {
    /** The uppercase title of a section, the `.section-title` element of the design. */
    fun sectionTitle(text: String, ikon: Ikon? = null): JLabel = JLabel(text.uppercase()).apply {
        font = UiKit.trackedFont(11f, 0.08, UiKit.Weight.BOLD)
        foreground = Palette.FG_3
        if (ikon != null) {
            icon = UiKit.icon(ikon, 16, Palette.LIME)
            iconTextGap = 8
        }
    }

    fun hint(text: String = ""): JLabel = JLabel(text).apply {
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
    }

    fun label(text: String, size: Float = 12.5f, color: Color = Palette.FG_2): JLabel = JLabel(text).apply {
        font = UiKit.font(size)
        foreground = color
    }
}

/**
 * Paints a thumbnail: the frame with the zoom, the texts of a layout style, and the YouTube length badge.
 * It keeps the scaled frame, so that a repaint does not scale the large frame again.
 */
internal class ThumbnailPainter {
    var frame: BufferedImage? = null
    var view = FrameView()
    var layout: ThumbnailLayout? = null

    /** The video length that YouTube shows in the bottom-right corner, or null. */
    var badge: String? = null

    /** A message in place of the thumbnail, for example when the app cannot read the frame. */
    var message: String? = null

    private var cache: Triple<Any, Dimension, BufferedImage>? = null

    fun paint(g: Graphics2D, width: Int, height: Int, radius: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.clip(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), radius * 2.0, radius * 2.0))
            val message = message
            if (message != null) {
                paintMessage(g2, width, height, message)
                return
            }
            val frame = frame
            if (frame != null) {
                // The scaled frame has the pixels of the screen, so that it is sharp on a HiDPI screen.
                val scale = g2.transform.scaleX.coerceAtLeast(1.0)
                val size = Dimension(ceil(width * scale).toInt().coerceAtLeast(1), ceil(height * scale).toInt().coerceAtLeast(1))
                val key = Pair(System.identityHashCode(frame), view)
                val scaled = cache?.takeIf { it.first == key && it.second == size }?.third
                    ?: ThumbnailRenderer.scaledFrame(frame, view, size.width, size.height).also { cache = Triple(key, size, it) }
                g2.drawImage(scaled, 0, 0, width, height, null)
            }
            g2.scale(width / ThumbnailRenderer.WIDTH.toDouble(), height / ThumbnailRenderer.HEIGHT.toDouble())
            if (frame == null) ThumbnailRenderer.paintFrame(g2, null, view)
            layout?.let { ThumbnailRenderer.paintLayout(g2, it) }
            badge?.let { ThumbnailRenderer.paintLengthBadge(g2, it) }
        } finally {
            g2.dispose()
        }
    }

    private fun paintMessage(g2: Graphics2D, width: Int, height: Int, message: String) {
        g2.color = Palette.INSET
        g2.fillRect(0, 0, width, height)
        if (height < 120) {
            val icon = UiKit.icon(Material2OutlinedAL.BROKEN_IMAGE, (height / 3).coerceIn(10, 28), Palette.RED)
            icon.paintIcon(null, g2, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
            return
        }
        val icon = UiKit.icon(Material2OutlinedAL.BROKEN_IMAGE, 40, Palette.RED)
        val font = UiKit.font(13f)
        val lines = message.split("\n")
        val lineH = 20
        val top = (height - icon.iconHeight - 10 - lines.size * lineH) / 2
        icon.paintIcon(null, g2, (width - icon.iconWidth) / 2, top)
        lines.forEachIndexed { index, line ->
            val x = (width - UiKit.textWidth(line, font)) / 2f
            UiKit.drawText(g2, line, font, Palette.FG_2, x, (top + icon.iconHeight + 10 + index * lineH).toFloat(), lineH.toFloat())
        }
    }
}

/** The large preview of the thumbnail. When the frame has a zoom, a drag moves the frame. */
internal class ThumbnailView(componentName: String, private val radius: Int = 4) : JPanel(null) {
    val painter = ThumbnailPainter()

    /** Gets the new frame position while the user drags. [done] is true when the user releases the mouse button. */
    var onPan: ((FrameView, done: Boolean) -> Unit)? = null

    private var dragStart: Pair<java.awt.Point, FrameView>? = null

    init {
        name = componentName
        isOpaque = false
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (painter.view.zoom > 1.0 && painter.message == null) dragStart = e.point to painter.view
            }

            override fun mouseDragged(e: MouseEvent) {
                val (start, view) = dragStart ?: return
                onPan?.invoke(panned(view, e.x - start.x, e.y - start.y), false)
            }

            override fun mouseReleased(e: MouseEvent) {
                val (start, view) = dragStart ?: return
                dragStart = null
                onPan?.invoke(panned(view, e.x - start.x, e.y - start.y), true)
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    /** The frame position after a drag of [dx], [dy] screen pixels: the frame moves with the pointer. */
    private fun panned(view: FrameView, dx: Int, dy: Int): FrameView {
        val scale = width / ThumbnailRenderer.WIDTH.toDouble()
        val zoom = view.zoom - 1.0
        if (scale <= 0.0 || zoom <= 0.0) return view
        return view.copy(
            panX = (view.panX - dx / scale / (zoom * ThumbnailRenderer.WIDTH)).coerceIn(0.0, 1.0),
            panY = (view.panY - dy / scale / (zoom * ThumbnailRenderer.HEIGHT)).coerceIn(0.0, 1.0),
        )
    }

    /** Shows the move cursor when a drag can move the frame. */
    fun updateCursor() {
        cursor = Cursor.getPredefinedCursor(if (painter.view.zoom > 1.0 && painter.message == null) Cursor.MOVE_CURSOR else Cursor.DEFAULT_CURSOR)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        painter.paint(g2, width, height, radius)
        val border = UiKit.smooth(g)
        try {
            border.color = Palette.LINE
            border.draw(RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, radius * 2.0, radius * 2.0))
        } finally {
            border.dispose()
        }
    }
}

/**
 * The frames of the point under the preview. A click or a drag sets the exact moment. The lime line shows the moment.
 * [onSeek] gets the position (0.0 to 1.0) and true when the user releases the mouse button.
 */
internal class MomentStrip(private val onSeek: (Double, Boolean) -> Unit) : JComponent() {
    var cells: List<Image?> = List(CELLS) { null }
        set(value) {
            field = value
            repaint()
        }

    var position = 0.5
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            repaint()
        }

    init {
        name = "share-strip"
        toolTipText = tr("share.strip.tooltip")
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = seek(e, false)
            override fun mouseDragged(e: MouseEvent) = seek(e, false)
            override fun mouseReleased(e: MouseEvent) = seek(e, true)
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    private fun seek(e: MouseEvent, done: Boolean) {
        if (!isEnabled || width <= 0) return
        onSeek((e.x.toDouble() / width).coerceIn(0.0, 1.0), done)
    }

    override fun getPreferredSize() = Dimension(400, 30)
    override fun getMinimumSize() = Dimension(100, 20)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            g2.clip(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0))
            g2.color = Palette.LINE
            g2.fillRect(0, 0, width, height)
            for (i in 0 until CELLS) {
                val left = i * (width + 1) / CELLS
                val right = (i + 1) * (width + 1) / CELLS - 1
                g2.color = Palette.INSET
                g2.fillRect(left, 0, right - left, height)
                val image = cells.getOrNull(i) ?: continue
                val old = g2.composite
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f)
                drawCover(g2, image, left, right - left)
                g2.composite = old
            }
            if (isEnabled) {
                val x = (position * width).roundToInt().coerceIn(1, width - 2)
                g2.color = Palette.BG
                g2.fillRect(x - 2, 0, 5, height)
                g2.color = Palette.LIME
                g2.fillRect(x - 1, 0, 3, height)
            }
        } finally {
            g2.dispose()
        }
    }

    private fun drawCover(g2: Graphics2D, image: Image, x: Int, w: Int) {
        val iw = image.getWidth(null).toDouble()
        val ih = image.getHeight(null).toDouble()
        if (iw <= 0 || ih <= 0 || w <= 0) return
        val scale = max(w / iw, height / ih)
        val sw = (w / scale).roundToInt()
        val sh = (height / scale).roundToInt()
        val sx = ((iw - sw) / 2).roundToInt()
        val sy = ((ih - sh) / 2).roundToInt()
        g2.drawImage(image, x, 0, x + w, height, sx, sy, sx + sw, sy + sh, null)
    }

    companion object {
        /** The strip shows this number of frames of the point. */
        const val CELLS = 10
    }
}

/** A card of a layout style: the current frame and texts in this style, and the name under it. */
internal class StyleCard(componentName: String, private val title: String) : JButton() {
    val painter = ThumbnailPainter()
    var selectedStyle = false
        set(value) {
            field = value
            repaint()
        }

    init {
        name = componentName
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        getAccessibleContext().accessibleName = title
        toolTipText = title
    }

    override fun getPreferredSize() = Dimension(120, 90)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val thumbH = (width * 9.0 / 16).roundToInt()
            UiKit.paintBox(g2, 0, 0, width, height, 5, Palette.RAISED, null)
            val inner = g2.create(1, 1, width - 2, thumbH) as Graphics2D
            try {
                painter.paint(inner, width - 2, thumbH, 0)
            } finally {
                inner.dispose()
            }
            val font = if (selectedStyle) UiKit.font(11.5f, UiKit.Weight.SEMIBOLD) else UiKit.font(11.5f)
            UiKit.drawText(g2, UiKit.ellipsize(title, font, width - 14f), font, if (selectedStyle) Palette.FG else Palette.FG_2, 7f, thumbH + 1f, (height - thumbH - 1).toFloat())
            val border = when {
                selectedStyle -> Palette.LIME
                model.isRollover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            g2.color = border
            g2.stroke = BasicStroke(if (selectedStyle) 2f else 1f)
            val inset = if (selectedStyle) 1.0 else 0.5
            g2.draw(RoundRectangle2D.Double(inset, inset, width - inset * 2, height - inset * 2, 10.0, 10.0))
            if (isFocusOwner) {
                g2.color = Palette.LIME_LINE
                g2.draw(RoundRectangle2D.Double(2.5, 2.5, width - 5.0, height - 5.0, 8.0, 8.0))
            }
        } finally {
            g2.dispose()
        }
    }
}

/** A small rounded choice under a field, for example "Final score": the `.chip` element of the design. */
internal class ChipButton(componentName: String, text: String) : JButton(text) {
    var chosen = false
        set(value) {
            field = value
            repaint()
        }

    init {
        name = componentName
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = UiKit.font(11f)
    }

    override fun getPreferredSize() = Dimension(ceil(UiKit.textWidth(text, font)).toInt() + 20, 22)
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!isEnabled) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)
            val hover = model.isRollover && isEnabled
            UiKit.paintBox(
                g2, 0, 0, width, height, height / 2, if (chosen) Palette.RAISED_2 else Palette.RAISED,
                when {
                    chosen -> Palette.LIME_LINE
                    hover -> Palette.HOVER_LINE
                    else -> Palette.LINE_2
                },
            )
            UiKit.drawText(g2, text, font, if (chosen || hover) Palette.FG else Palette.FG_2, 10f, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }
}

/** A text button in the sage color, for example "Use the presets": the `.link` element of the design. */
internal class LinkButton(componentName: String, text: String) : JButton(text) {
    init {
        name = componentName
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = UiKit.font(12f)
    }

    override fun getPreferredSize() = Dimension(ceil(UiKit.textWidth(text, font)).toInt() + 4, 18)
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            val color = if (hover) Palette.FG else Palette.SAGE
            UiKit.drawText(g2, text, font, color, 2f, 0f, height.toFloat())
            if (hover) {
                g2.color = color
                g2.fillRect(2, height - 3, ceil(UiKit.textWidth(text, font)).toInt(), 1)
            }
        } finally {
            g2.dispose()
        }
    }
}

/**
 * A round color swatch of the Colors tab. [color] null is the "Auto" pill. [custom] is the "Other color" swatch:
 * it shows [colorWheel] when the role has no other color, and the color of the role when it has one.
 */
internal class ColorSwatch(
    componentName: String,
    tooltip: String,
    var color: Color?,
    private val custom: Boolean = false,
    private val colorWheel: List<Color> = emptyList(),
) : JButton() {
    var chosen = false
        set(value) {
            field = value
            repaint()
        }

    init {
        name = componentName
        toolTipText = tooltip
        getAccessibleContext().accessibleName = tooltip
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = UiKit.font(11f, UiKit.Weight.SEMIBOLD)
    }

    private val isAuto get() = color == null && !custom

    override fun getPreferredSize() =
        if (isAuto) Dimension(ceil(UiKit.textWidth(AUTO, font)).toInt() + 16 + RING * 2, SIZE + RING * 2) else Dimension(SIZE + RING * 2, SIZE + RING * 2)

    override fun getMaximumSize() = preferredSize
    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val d = SIZE.toDouble()
            val x = (width - (if (isAuto) width - RING * 2.0 else d)) / 2
            val y = (height - d) / 2
            val w = if (isAuto) width - RING * 2.0 else d
            val grow = if (model.isRollover && !chosen) 1.0 else 0.0
            val shape = RoundRectangle2D.Double(x - grow, y - grow, w + grow * 2, d + grow * 2, d + grow * 2, d + grow * 2)
            val color = color
            when {
                isAuto -> {
                    g2.color = Palette.RAISED_2
                    g2.fill(shape)
                    UiKit.drawText(g2, AUTO, font, if (chosen) Palette.FG else Palette.FG_2, (x + 8).toFloat(), y.toFloat(), d.toFloat())
                }
                custom && (color == null || !chosen) -> {
                    val slices = colorWheel.ifEmpty { listOf(Palette.FG_2) }
                    slices.forEachIndexed { index, slice ->
                        g2.color = slice
                        val extent = 360.0 / slices.size
                        g2.fill(Arc2D.Double(x - grow, y - grow, d + grow * 2, d + grow * 2, 90 - index * extent - extent, extent + 1, Arc2D.PIE))
                    }
                }
                else -> {
                    g2.color = color
                    g2.fill(shape)
                }
            }
            g2.color = Palette.HIGHLIGHT_3
            g2.stroke = BasicStroke(1f)
            if (!isAuto) g2.draw(Ellipse2D.Double(x + 0.5, y + 0.5, d - 1, d - 1))
            if (chosen) {
                g2.color = Palette.FG
                g2.stroke = BasicStroke(2f)
                g2.draw(RoundRectangle2D.Double(x - 3, y - 3, w + 6, d + 6, d + 6, d + 6))
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val SIZE = 22
        const val RING = 4
        val AUTO: String get() = tr("override.part.auto")
    }
}

/** A yellow note with an icon, the `.note` element of the design. It is hidden when it has no text. */
internal class NoteBox(componentName: String, ikon: Ikon) : JPanel(null), HeightForWidth {
    private val icon = UiKit.icon(ikon, 17, Palette.YELLOW)
    private val body = WrapText(emptyList(), 1.45f)

    var text: String = ""
        set(value) {
            if (field == value) return
            field = value
            body.runs = if (value.isEmpty()) emptyList() else listOf(TextRun(value, UiKit.font(12.5f), Palette.YELLOW_TEXT))
            isVisible = value.isNotEmpty()
            revalidate()
            repaint()
        }

    init {
        name = componentName
        isOpaque = false
        isVisible = false
        add(body)
    }

    private fun bodyWidth(width: Int) = (width - PAD_X * 2 - icon.iconWidth - GAP).coerceAtLeast(1)

    override fun heightForWidth(width: Int) = PAD_Y * 2 + max(body.heightForWidth(bodyWidth(width)), icon.iconHeight)

    override fun getPreferredSize() = Dimension(260, heightForWidth(if (width > 0) width else 300))

    override fun doLayout() {
        val w = bodyWidth(width)
        body.setBounds(PAD_X + icon.iconWidth + GAP, PAD_Y, w, body.heightForWidth(w))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.YELLOW_TINT, Palette.YELLOW_LINE)
            icon.paintIcon(this, g2, PAD_X, PAD_Y + 1)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 10
        const val PAD_Y = 7
        const val GAP = 8
    }
}

/** A line of the Save block: the state of the saved file of one video type. */
internal class FileLine(componentName: String) : JPanel(null), HeightForWidth {
    private val body = WrapText(emptyList(), 1.3f)
    private var icon: Icon? = null

    /** The text of the line, for the tests. */
    var text: String = ""
        private set

    init {
        name = componentName
        isOpaque = false
        add(body)
    }

    fun show(text: String, ikon: Ikon, iconColor: Color, current: Boolean, tooltip: String) {
        this.text = text
        icon = UiKit.icon(ikon, ICON, iconColor)
        body.runs = listOf(TextRun(text, UiKit.font(11.5f), if (current) Palette.FG else Palette.FG_3))
        toolTipText = tooltip
        body.toolTipText = tooltip
        revalidate()
        repaint()
    }

    private fun bodyWidth(width: Int) = (width - ICON - GAP).coerceAtLeast(1)

    override fun heightForWidth(width: Int) = max(ROW, body.heightForWidth(bodyWidth(width)) + 2)

    override fun getPreferredSize() = Dimension(160, heightForWidth(if (width > 0) width else 160))

    override fun doLayout() {
        val w = bodyWidth(width)
        body.setBounds(ICON + GAP, 1, w, body.heightForWidth(w))
    }

    override fun paintComponent(g: Graphics) {
        icon?.paintIcon(this, g, 0, 1)
    }

    private companion object {
        const val ICON = 15
        const val GAP = 5
        const val ROW = 18
    }
}
