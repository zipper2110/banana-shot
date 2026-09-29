package org.litvin.ui.commons

import org.kordamp.ikonli.feather.Feather
import java.awt.BasicStroke
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.Insets
import java.awt.LinearGradientPaint
import java.awt.Graphics2D
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The color picker of the app, in place of JColorChooser: design/dialogs-redesign/color-picker.html.
 * The current and the new color show side by side; a click on the current color selects it again.
 * "Swatches" has a palette and the recent colors. "Custom" has a saturation and value square, a hue bar,
 * the HSV, HSL, RGB and CMYK values, and the hex code.
 */
internal object ColorPickerDialog {
    /** The colors that the user confirmed with OK in this session, the newest first. */
    private val recent = mutableListOf<Color>()
    private const val RECENT_MAX = 12
    private const val WIDTH = 404

    /** Opens the picker and returns the new color, or null after Cancel. */
    fun pick(parent: Component?, title: String, initial: Color): Color? {
        var result: Color? = null
        val dialog = build(parent, title, initial) { result = it }
        dialog.setLocationRelativeTo(parent?.let { SwingUtilities.getWindowAncestor(it) ?: it })
        dialog.isVisible = true
        return result
    }

    /** Builds the packed picker without showing it. [onOk] gets the new color. [custom] opens the custom view. */
    internal fun build(parent: Component?, title: String, initial: Color, custom: Boolean = false, onOk: (Color) -> Unit): JDialog {
        val dialog = DialogKit.modal(parent, title)
        dialog.name = "color-picker"
        val picker = Picker(Color(initial.rgb and 0xFFFFFF))
        if (custom) picker.showCustom()
        val ok = UiButton("OK", kind = UiButton.Kind.LIME).apply {
            name = "color-picker-ok"
            addActionListener {
                onOk(picker.color)
                remember(picker.color)
                dialog.dispose()
            }
        }
        val cancel = UiButton("Cancel").apply {
            name = "color-picker-cancel"
            addActionListener { dialog.dispose() }
        }
        val reset = UiButton("Reset", Feather.ROTATE_CCW, UiButton.Kind.QUIET).apply {
            name = "color-picker-reset"
            toolTipText = "Go back to the current color"
            addActionListener { picker.setColor(picker.initial) }
        }
        picker.onConfirm = { ok.doClick() }
        dialog.contentPane = DialogKit.content(
            WIDTH,
            DialogKit.head(title),
            picker,
            DialogKit.footer(left = listOf(reset), right = listOf(cancel, ok)),
        )
        dialog.rootPane.defaultButton = ok
        dialog.isResizable = false
        dialog.pack()
        return dialog
    }

    private fun remember(color: Color) {
        recent.remove(color)
        recent.add(0, color)
        while (recent.size > RECENT_MAX) recent.removeAt(recent.lastIndex)
    }

    fun hex(color: Color) = "#%06X".format(color.rgb and 0xFFFFFF)

    /** The body of the picker. The color is kept as hue (0..360), saturation and value (0..1). */
    private class Picker(val initial: Color) : Stack(pad = Insets(8, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 12) {
        private var h = 0f
        private var s = 0f
        private var v = 0f
        private var model = "RGB"
        private var syncing = false
        var onConfirm: () -> Unit = {}

        val color: Color get() = Color(Color.HSBtoRGB(h / 360f, s, v) and 0xFFFFFF)

        private val compare = Compare()
        private val view = SegmentedChoice(
            "color-picker-view",
            listOf(SegmentedChoice.Option("swatches", "Swatches"), SegmentedChoice.Option("custom", "Custom")),
        )
        private val cards = CardLayout()
        private val panes = JPanel(cards).apply { isOpaque = false }
        private val palette = SwatchGrid(paletteColors(), "color-picker-swatch")
        private val recentGrid = SwatchGrid(recent.toList(), "color-picker-recent")
        private val square = SvSquare()
        private val hueBar = HueBar()
        private val modelChoice = SegmentedChoice(
            "color-picker-model",
            listOf("HSV", "HSL", "RGB", "CMYK").map { SegmentedChoice.Option(it, it) },
        )
        private val numbers = JPanel(GridLayout(1, 4, 8, 0)).apply { isOpaque = false }
        private val numberFields = List(4) { index -> JTextField().apply { name = "color-picker-value-$index" } }
        private val numberLabels = List(4) { JLabel() }
        private val numberCells = List(4) { index ->
            Stack(gap = 3).apply {
                add(numberLabels[index].apply {
                    font = UiKit.font(11f)
                    foreground = UiKit.FG_3
                })
                add(DialogKit.inputBox(numberFields[index], 28))
                numberFields[index].horizontalAlignment = JTextField.RIGHT
            }
        }
        private val hexField = JTextField().apply {
            name = "color-picker-hex"
            font = MonoFont.of(13f)
        }

        init {
            add(compare)
            add(view)
            add(panes)
            view.selected = "swatches"
            view.onChange { cards.show(panes, it) }

            val recentCaption = JLabel("RECENT").apply {
                font = UiKit.trackedFont(10.5f, 0.1, UiKit.Weight.BOLD)
                foreground = UiKit.FG_3
            }
            val recentEmpty = JLabel("The colors that you select show here.").apply {
                font = UiKit.font(12f)
                foreground = UiKit.FG_3
                preferredSize = Dimension(0, 26)
            }
            panes.add(Stack(gap = 0).apply {
                add(palette, gapAfter = 14)
                add(recentCaption, gapAfter = 6)
                add(if (recent.isEmpty()) recentEmpty else recentGrid)
            }, "swatches")

            val hexRow = JPanel(null).apply {
                isOpaque = false
                val label = JLabel("Hex").apply {
                    font = UiKit.font(11f)
                    foreground = UiKit.FG_3
                }
                val box = DialogKit.inputBox(hexField, 28)
                add(label)
                add(box)
                preferredSize = Dimension(200, 28)
                label.setBounds(0, 0, 30, 28)
                box.setBounds(34, 0, 110, 28)
            }
            panes.add(Stack(gap = 10).apply {
                add(square)
                add(hueBar)
                add(modelChoice, gapAfter = 8)
                add(numbers)
                add(hexRow)
            }, "custom")

            modelChoice.selected = model
            modelChoice.onChange {
                model = it
                syncNumbers()
            }
            palette.onPick = { setColor(it) }
            recentGrid.onPick = { setColor(it) }
            palette.onDoubleClick = { onConfirm() }
            recentGrid.onDoubleClick = { onConfirm() }
            numberFields.forEach { field ->
                field.document.addDocumentListener(changes { if (field.hasFocus()) readNumbers() })
                field.addFocusListener(object : FocusAdapter() {
                    override fun focusGained(e: FocusEvent) = field.selectAll()
                    override fun focusLost(e: FocusEvent) = syncNumbers()
                })
            }
            hexField.document.addDocumentListener(changes { if (hexField.hasFocus()) parseHex(hexField.text)?.let { setColor(it, fromHex = true) } })
            hexField.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = sync(fromHex = false)
            })
            setColor(initial)
        }

        fun showCustom() {
            view.selected = "custom"
            cards.show(panes, "custom")
        }

        fun setColor(color: Color, fromHex: Boolean = false) {
            val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
            // A gray color has no hue. Keep the hue of the bar, so the square does not jump to red.
            if (hsb[1] > 0f && hsb[2] > 0f) h = hsb[0] * 360f
            s = hsb[1]
            v = hsb[2]
            sync(fromHex)
        }

        private fun sync(fromHex: Boolean = false) {
            val c = color
            compare.newColor = c
            square.repaint()
            hueBar.repaint()
            palette.selected = c
            recentGrid.selected = c
            if (!fromHex && !hexField.hasFocus()) {
                syncing = true
                hexField.text = hex(c)
                syncing = false
            }
            syncNumbers()
        }

        /** The labels, the limits and the values of the number fields for the selected color model. */
        private fun fields(): List<Pair<String, Int>> {
            val c = color
            return when (model) {
                "RGB" -> listOf("Red" to c.red, "Green" to c.green, "Blue" to c.blue)
                "HSV" -> listOf("Hue" to h.roundToInt() % 360, "Saturation" to (s * 100).roundToInt(), "Value" to (v * 100).roundToInt())
                "HSL" -> {
                    val (_, sl, l) = hsl(c)
                    listOf("Hue" to h.roundToInt() % 360, "Saturation" to (sl * 100).roundToInt(), "Lightness" to (l * 100).roundToInt())
                }
                else -> {
                    val k = 1f - max(c.red, max(c.green, c.blue)) / 255f
                    fun part(x: Int) = if (k >= 1f) 0 else (((1f - x / 255f - k) / (1f - k)) * 100).roundToInt()
                    listOf("Cyan" to part(c.red), "Magenta" to part(c.green), "Yellow" to part(c.blue), "Black" to (k * 100).roundToInt())
                }
            }
        }

        private fun limits(): List<Int> = when (model) {
            "RGB" -> listOf(255, 255, 255)
            "HSV", "HSL" -> listOf(359, 100, 100)
            else -> listOf(100, 100, 100, 100)
        }

        private fun syncNumbers() {
            val values = fields()
            if (numbers.componentCount != values.size) {
                // CMYK has four values; the other models have three.
                numbers.removeAll()
                numbers.layout = GridLayout(1, values.size, 8, 0)
                numberCells.take(values.size).forEach { numbers.add(it) }
            }
            values.forEachIndexed { index, (label, value) ->
                numberLabels[index].text = label
                val field = numberFields[index]
                if (!field.hasFocus()) {
                    syncing = true
                    field.text = value.toString()
                    syncing = false
                }
            }
            numbers.revalidate()
            numbers.repaint()
        }

        private fun readNumbers() {
            if (syncing) return
            val limits = limits()
            val values = limits.indices.map { i -> (numberFields[i].text.trim().toIntOrNull() ?: return).coerceIn(0, limits[i]) }
            val rgb: Color = when (model) {
                "RGB" -> Color(values[0], values[1], values[2])
                "HSV" -> Color(Color.HSBtoRGB(values[0] / 360f, values[1] / 100f, values[2] / 100f))
                "HSL" -> fromHsl(values[0].toFloat(), values[1] / 100f, values[2] / 100f)
                else -> {
                    val k = values[3] / 100f
                    fun part(x: Int) = (255 * (1 - x / 100f) * (1 - k)).roundToInt().coerceIn(0, 255)
                    Color(part(values[0]), part(values[1]), part(values[2]))
                }
            }
            val hsb = Color.RGBtoHSB(rgb.red, rgb.green, rgb.blue, null)
            h = if (model == "HSV" || model == "HSL") values[0].toFloat() else if (hsb[1] > 0f && hsb[2] > 0f) hsb[0] * 360f else h
            s = hsb[1]
            v = hsb[2]
            sync()
        }

        private fun changes(action: () -> Unit) = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = run()
            override fun removeUpdate(e: DocumentEvent) = run()
            override fun changedUpdate(e: DocumentEvent) = run()
            private fun run() {
                if (!syncing) action()
            }
        }

        /** The current and the new color. A click on the current color selects it again. */
        private inner class Compare : JComponent() {
            var newColor: Color = initial
                set(value) {
                    field = value
                    repaint()
                }

            init {
                toolTipText = "Current color. Click to use it again."
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.x < width / 2) setColor(initial)
                    }
                })
            }

            override fun getPreferredSize() = Dimension(300, 54)

            override fun getToolTipText(event: MouseEvent): String? = if (event.x < width / 2) toolTipText else null

            override fun paintComponent(g: Graphics) {
                val g2 = UiKit.smooth(g)
                try {
                    val clip = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 12.0, 12.0)
                    g2.clip(clip)
                    chip(g2, 0, width / 2, initial, "CURRENT")
                    chip(g2, width / 2, width - width / 2, newColor, "NEW")
                    g2.clip = null
                    UiKit.paintBox(g2, 0, 0, width, height, 6, null, UiKit.LINE_2)
                } finally {
                    g2.dispose()
                }
            }

            private fun chip(g2: Graphics2D, x: Int, w: Int, color: Color, label: String) {
                g2.color = color
                g2.fillRect(x, 0, w, height)
                val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
                val text = if (hsb[2] > 0.6f && hsb[1] < 0.6f) Color(0x111111) else Color.WHITE
                UiKit.drawText(g2, label, UiKit.trackedFont(10.5f, 0.08, UiKit.Weight.BOLD), text, x + 10f, 7f, 15f)
                UiKit.drawText(g2, hex(color), MonoFont.of(12.5f), text, x + 10f, height - 24f, 17f)
            }
        }

        /** The saturation (left to right) and value (bottom to top) square of the current hue. */
        private inner class SvSquare : JComponent() {
            init {
                cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                val mouse = object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) = pick(e)
                    override fun mouseDragged(e: MouseEvent) = pick(e)
                }
                addMouseListener(mouse)
                addMouseMotionListener(mouse)
            }

            private fun pick(e: MouseEvent) {
                s = (e.x.toFloat() / max(1, width - 1)).coerceIn(0f, 1f)
                v = (1f - e.y.toFloat() / max(1, height - 1)).coerceIn(0f, 1f)
                sync()
            }

            override fun getPreferredSize() = Dimension(300, 150)

            override fun paintComponent(g: Graphics) {
                val g2 = UiKit.smooth(g)
                try {
                    val shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 12.0, 12.0)
                    g2.color = Color(Color.HSBtoRGB(h / 360f, 1f, 1f))
                    g2.fill(shape)
                    g2.paint = GradientPaint(0f, 0f, Color.WHITE, width.toFloat(), 0f, Color(255, 255, 255, 0))
                    g2.fill(shape)
                    g2.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 0), 0f, height.toFloat(), Color.BLACK)
                    g2.fill(shape)
                    UiKit.paintBox(g2, 0, 0, width, height, 6, null, UiKit.LINE_2)
                    val x = s * (width - 1)
                    val y = (1 - v) * (height - 1)
                    g2.color = color
                    g2.fill(Ellipse2D.Double(x - 7.0, y - 7.0, 14.0, 14.0))
                    g2.color = Color(0, 0, 0, 150)
                    g2.stroke = BasicStroke(1f)
                    g2.draw(Ellipse2D.Double(x - 8.0, y - 8.0, 16.0, 16.0))
                    g2.color = Color.WHITE
                    g2.stroke = BasicStroke(2f)
                    g2.draw(Ellipse2D.Double(x - 6.0, y - 6.0, 12.0, 12.0))
                } finally {
                    g2.dispose()
                }
            }
        }

        /** The hue bar. Left and Right change the hue by 1 degree. */
        private inner class HueBar : JComponent() {
            init {
                isFocusable = true
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = "Hue"
                val mouse = object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        requestFocusInWindow()
                        pick(e)
                    }

                    override fun mouseDragged(e: MouseEvent) = pick(e)
                }
                addMouseListener(mouse)
                addMouseMotionListener(mouse)
                addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        val step = when (e.keyCode) {
                            KeyEvent.VK_LEFT, KeyEvent.VK_DOWN -> -1
                            KeyEvent.VK_RIGHT, KeyEvent.VK_UP -> 1
                            else -> return
                        }
                        h = ((h + step) % 360f + 360f) % 360f
                        sync()
                        e.consume()
                    }
                })
                addFocusListener(object : FocusAdapter() {
                    override fun focusGained(e: FocusEvent) = repaint()
                    override fun focusLost(e: FocusEvent) = repaint()
                })
            }

            private fun pick(e: MouseEvent) {
                h = ((e.x - THUMB / 2f) / max(1, width - THUMB) * 359f).coerceIn(0f, 359f)
                sync()
            }

            override fun getPreferredSize() = Dimension(300, 20)

            override fun paintComponent(g: Graphics) {
                val g2 = UiKit.smooth(g)
                try {
                    val top = (height - TRACK) / 2f
                    val fractions = floatArrayOf(0f, 1 / 6f, 2 / 6f, 3 / 6f, 4 / 6f, 5 / 6f, 1f)
                    val colors = arrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
                    g2.paint = LinearGradientPaint(THUMB / 2f, 0f, width - THUMB / 2f, 0f, fractions, colors)
                    g2.fill(RoundRectangle2D.Float(0f, top, width.toFloat(), TRACK.toFloat(), TRACK.toFloat(), TRACK.toFloat()))
                    val x = THUMB / 2f + (width - THUMB) * (h / 359f)
                    val cy = height / 2f
                    if (isFocusOwner) {
                        g2.color = Color(161, 254, 0, 64)
                        g2.fill(Ellipse2D.Float(x - THUMB / 2f - 2, cy - THUMB / 2f - 2, THUMB + 4f, THUMB + 4f))
                    }
                    g2.color = Color.BLACK
                    g2.fill(Ellipse2D.Float(x - THUMB / 2f, cy - THUMB / 2f, THUMB.toFloat(), THUMB.toFloat()))
                    g2.color = Color.WHITE
                    g2.fill(Ellipse2D.Float(x - THUMB / 2f + 1, cy - THUMB / 2f + 1, THUMB - 2f, THUMB - 2f))
                    g2.color = Color(Color.HSBtoRGB(h / 360f, 1f, 1f))
                    g2.fill(Ellipse2D.Float(x - 4f, cy - 4f, 8f, 8f))
                } finally {
                    g2.dispose()
                }
            }
        }
    }

    /** A grid of color squares with 12 columns. A click selects a color; a double-click also confirms it. */
    private class SwatchGrid(private val colors: List<Color>, private val componentName: String) : JComponent() {
        var onPick: (Color) -> Unit = {}
        var onDoubleClick: () -> Unit = {}
        var selected: Color? = null
            set(value) {
                field = value
                repaint()
            }
        private var hover = -1

        init {
            name = componentName
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val mouse = object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = indexAt(e.x, e.y)
                    if (index < 0) return
                    onPick(colors[index])
                    if (e.clickCount == 2) onDoubleClick()
                }

                override fun mouseMoved(e: MouseEvent) {
                    val index = indexAt(e.x, e.y)
                    if (index != hover) {
                        hover = index
                        toolTipText = colors.getOrNull(index)?.let { hex(it) }
                        repaint()
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    hover = -1
                    repaint()
                }
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
        }

        private val rows get() = (colors.size + COLUMNS - 1) / COLUMNS

        private fun cell(): Float = (width - GAP * (COLUMNS - 1f)) / COLUMNS

        private fun indexAt(x: Int, y: Int): Int {
            val size = cell() + GAP
            val column = (x / size).toInt()
            val row = (y / size).toInt()
            val index = row * COLUMNS + column
            return if (column in 0 until COLUMNS && index in colors.indices) index else -1
        }

        // The cells follow the width, so the height allows cells a little larger than 26 px.
        override fun getPreferredSize() = Dimension(COLUMNS * 26 + (COLUMNS - 1) * GAP, rows * 27 + (rows - 1) * GAP)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val size = cell()
                colors.forEachIndexed { index, color ->
                    val x = (index % COLUMNS) * (size + GAP)
                    val y = (index / COLUMNS) * (size + GAP)
                    val isSelected = selected?.rgb == color.rgb
                    if (isSelected) {
                        g2.color = UiKit.LIME
                        g2.fill(RoundRectangle2D.Float(x - 2.5f, y - 2.5f, size + 5f, size + 5f, 8f, 8f))
                        g2.color = DialogKit.DIALOG_BG
                        g2.fill(RoundRectangle2D.Float(x - 1f, y - 1f, size + 2f, size + 2f, 6f, 6f))
                    }
                    g2.color = color
                    g2.fill(RoundRectangle2D.Float(x, y, size, size, 6f, 6f))
                    g2.color = if (index == hover) Color.WHITE else Color(255, 255, 255, 31)
                    g2.stroke = BasicStroke(1f)
                    g2.draw(RoundRectangle2D.Float(x + 0.5f, y + 0.5f, size - 1f, size - 1f, 5f, 5f))
                }
            } finally {
                g2.dispose()
            }
        }

        private companion object {
            const val COLUMNS = 12
            const val GAP = 4
        }
    }

    /** The palette: a gray row, then 12 hues in light to dark rows, like the palette of the design. */
    private fun paletteColors(): List<Color> {
        val grays = (0 until 12).map { Color(Color.HSBtoRGB(0f, 0f, 1f - it / 11f)) }
        val rows = listOf(0.25f to 1f, 0.5f to 1f, 0.8f to 1f, 1f to 0.85f, 1f to 0.6f, 1f to 0.38f).flatMap { (sat, value) ->
            (0 until 12).map { Color(Color.HSBtoRGB(it * 30f / 360f, sat, value)) }
        }
        return (grays + rows).map { Color(it.rgb and 0xFFFFFF) }
    }

    private fun parseHex(text: String): Color? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 6) return null
        return digits.toIntOrNull(16)?.let { Color(it) }
    }

    /** Hue (0..360), saturation and lightness (0..1) of [color]. */
    private fun hsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        val d = hi - lo
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
        return Triple(Color.RGBtoHSB(color.red, color.green, color.blue, null)[0] * 360f, s, l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Color {
        val a = s * min(l, 1 - l)
        fun f(n: Float): Int {
            val k = (n + h / 30f) % 12f
            return ((l - a * max(-1f, min(k - 3f, min(9f - k, 1f)))) * 255).roundToInt().coerceIn(0, 255)
        }
        return Color(f(0f), f(8f), f(4f))
    }

    private const val TRACK = 10
    private const val THUMB = 16
}
