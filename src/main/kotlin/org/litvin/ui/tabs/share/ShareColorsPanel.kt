package org.litvin.ui.tabs.share

import org.litvin.share.ColorRole
import org.litvin.share.ColorSet
import org.litvin.share.StyleLookV1
import org.litvin.share.ThumbnailColorSets
import org.litvin.share.ThumbnailLooks
import org.litvin.share.ThumbnailStyle
import org.litvin.ui.commons.DefaultFillSliderUI
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.Insets
import java.awt.geom.Ellipse2D
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.math.roundToInt
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/**
 * The Colors tab of the side column: the color sets, then one row for each color role of the style.
 * [onRole] gets a role and "#RRGGBB" or "auto". [onStrength] gets the strength of the shade and true while the user drags.
 * [onPickColor] opens the color picker for a role.
 */
internal class ShareColorsPanel(
    private val onSet: (String) -> Unit,
    private val onRole: (ColorRole, String) -> Unit,
    private val onStrength: (Double, Boolean) -> Unit,
    private val onPickColor: (ColorRole, Color) -> Unit,
    private val onResetColors: () -> Unit,
) : Stack(pad = Insets(14, 16, 16, 16), gap = 12) {
    private val setButtons = ThumbnailColorSets.all.map { set -> ColorSetButton(set).apply { addActionListener { onSet(set.id) } } }
    private val changed = ShareUi.label(tr("share.colors.changed"), 12f, Palette.YELLOW)
    private val reset = LinkButton("share-reset-colors", tr("share.colors.reset"))
    private val roles = Stack(gap = 12)
    private var rolesFor: ThumbnailStyle? = null
    private val rows = HashMap<ColorRole, RoleRow>()
    private var strength: JSlider? = null
    private var strengthValue: JLabel? = null
    private var updating = false

    init {
        name = "share-colors"
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(ShareUi.sectionTitle(tr("share.colors.set")), BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                isOpaque = false
                add(changed)
                add(reset)
            }, BorderLayout.EAST)
        }, gapAfter = 8)
        add(JPanel(GridLayout(0, 3, 6, 6)).apply {
            isOpaque = false
            setButtons.forEach { add(it) }
        }, gapAfter = 14)
        add(Divider(), gapAfter = 14)
        add(roles)
        add(WrapText(tr("share.colors.autoNote"), UiKit.font(11.5f), Palette.FG_3, 1.4f, 300))
        reset.addActionListener { onResetColors() }
    }

    /** The role rows are made again only when the style changes, so that a drag of the slider is not interrupted. */
    private fun buildRoles(style: ThumbnailStyle) {
        roles.removeAll()
        rows.clear()
        strength = null
        strengthValue = null
        for ((role, use) in style.roles) {
            val row = RoleRow(role, use)
            rows[role] = row
            roles.add(row)
        }
        rolesFor = style
    }

    fun show(style: ThumbnailStyle, look: StyleLookV1?) {
        if (rolesFor != style) buildRoles(style)
        val set = ThumbnailLooks.colorSet(style, look)
        val customized = look?.customized == true
        setButtons.forEach { it.chosen = it.set.id == set.id && !customized }
        for ((role, row) in rows) row.show(ThumbnailLooks.roleValue(style, look, role))
        val k = ThumbnailLooks.strength(look)
        strength?.let { slider ->
            updating = true
            try {
                if (!slider.valueIsAdjusting) slider.value = (k * 100).roundToInt()
            } finally {
                updating = false
            }
        }
        strengthValue?.text = "${DisplayFormats.integer((k * 100).roundToInt())}%"
        changed.isVisible = customized
        reset.isVisible = look?.colorsEdited == true
        revalidate()
        repaint()
    }

    /** One role: its name, the parts that use it, the swatches, and "Other color". The shade has a strength slider. */
    private inner class RoleRow(private val role: ColorRole, use: String) : Stack(gap = 5) {
        private val auto = if (role.auto) ColorSwatch("share-role-${role.key}-auto", tr("share.colors.auto.tooltip"), null) else null
        private val swatches = ThumbnailColorSets.swatches(role).map { named ->
            ColorSwatch("share-role-${role.key}-${named.id}", named.name, Color(named.rgb)) to named.rgb
        }
        private val custom = ColorSwatch(
            "share-role-${role.key}-other", tr("share.colors.other"), null, custom = true,
            colorWheel = ThumbnailColorSets.swatches(ColorRole.ACCENT).take(7).map { Color(it.rgb) },
        )
        private var value: String = ThumbnailLooks.AUTO

        init {
            name = "share-role-${role.key}"
            add(JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(ShareUi.label(role.title, 12.5f, Palette.FG), BorderLayout.WEST)
                add(ShareUi.label(use, 11f, Palette.FG_3), BorderLayout.EAST)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                auto?.let {
                    add(it)
                    it.addActionListener { onRole(role, ThumbnailLooks.AUTO) }
                }
                swatches.forEach { (swatch, rgb) ->
                    add(swatch)
                    swatch.addActionListener { onRole(role, ThumbnailLooks.hex(rgb)) }
                }
                add(custom)
                custom.addActionListener {
                    val current = ThumbnailLooks.parseHex(value) ?: 0xFFFFFF
                    onPickColor(role, Color(current))
                }
            })
            if (role == ColorRole.SHADE) add(strengthRow())
        }

        private fun strengthRow(): JComponent {
            val slider = DefaultFillSliderUI.slider(0, (ThumbnailLooks.MAX_STRENGTH * 100).roundToInt(), 100).apply {
                name = "share-shade-strength"
                getAccessibleContext().accessibleName = tr("share.colors.strength.accessible")
                addChangeListener {
                    if (!updating) onStrength(value / 100.0, valueIsAdjusting)
                }
            }
            val value = ShareUi.label("100%", 11.5f, Palette.FG_2).apply { preferredSize = Dimension(40, 20) ; horizontalAlignment = JLabel.RIGHT }
            strength = slider
            strengthValue = value
            return JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(ShareUi.label(tr("share.colors.strength"), 11.5f, Palette.FG_2), BorderLayout.WEST)
                add(slider, BorderLayout.CENTER)
                add(value, BorderLayout.EAST)
            }
        }

        fun show(value: String) {
            this.value = value
            var known = false
            auto?.let {
                it.chosen = value == ThumbnailLooks.AUTO
                known = known || it.chosen
            }
            val rgb = ThumbnailLooks.parseHex(value)
            swatches.forEach { (swatch, color) ->
                swatch.chosen = rgb == color
                known = known || swatch.chosen
            }
            custom.chosen = !known
            custom.color = if (known || rgb == null) null else Color(rgb)
        }
    }

    /** A color set: three dots (accent, panel, text) and the name. */
    private class ColorSetButton(val set: ColorSet) : JButton() {
        var chosen = false
            set(value) {
                field = value
                repaint()
            }

        init {
            name = "share-set-${set.id}"
            getAccessibleContext().accessibleName = set.name
            toolTipText = set.name
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            isRolloverEnabled = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        override fun getPreferredSize() = Dimension(90, 48)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val hover = model.isRollover
                UiKit.paintBox(g2, 0, 0, width, height, 5, Palette.RAISED, if (chosen) null else if (hover) Palette.HOVER_LINE else Palette.LINE_2)
                if (chosen) {
                    g2.color = Palette.LIME
                    g2.stroke = BasicStroke(2f)
                    g2.draw(java.awt.geom.RoundRectangle2D.Double(1.0, 1.0, width - 2.0, height - 2.0, 10.0, 10.0))
                }
                listOf(set.accent, set.panel, set.text).forEachIndexed { index, rgb ->
                    val x = 8.0 + index * 10
                    g2.color = Palette.RAISED
                    g2.fill(Ellipse2D.Double(x - 2, 6.0, 18.0, 18.0))
                    g2.color = Color(rgb)
                    g2.fill(Ellipse2D.Double(x, 8.0, 14.0, 14.0))
                    g2.color = Palette.HIGHLIGHT_2
                    g2.draw(Ellipse2D.Double(x + 0.5, 8.5, 13.0, 13.0))
                }
                val font = UiKit.font(11.5f)
                UiKit.drawText(g2, set.name, font, if (chosen || hover) Palette.FG else Palette.FG_2, 8f, 26f, 16f)
            } finally {
                g2.dispose()
            }
        }
    }

    /** A thin line between two blocks. */
    private class Divider : JComponent() {
        override fun getPreferredSize() = Dimension(10, 1)

        override fun paintComponent(g: Graphics) {
            g.color = Palette.LINE
            g.fillRect(0, 0, width, 1)
        }
    }
}
