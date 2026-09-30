package org.litvin.ui.commons

import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder
import javax.swing.plaf.UIResource
import javax.swing.text.JTextComponent

/**
 * Experimental runtime theme. [apply] changes the seed colors of [Palette] and updates the open windows.
 *
 * A component that reads a Palette token when it paints gets the new color at the next repaint.
 * A component that keeps a color from the time it was built gets the new color when its color is equal to an old
 * token value: backgrounds, foregrounds, text and table colors, line and matte borders, and Ikonli icons.
 * Other colors change only when the component is built again, for example FlatLaf style strings and cached images.
 */
internal object Theme {
    fun apply(accent: Color, background: Color) {
        if (accent.rgb == Palette.accent.rgb && background.rgb == Palette.background.rgb) return
        val before = Palette.seededValues()
        Palette.setSeeds(accent, background)
        val after = Palette.seededValues()
        // The key is the ARGB value, because a UIResource color is not equal to a plain color with the same value.
        val changes = HashMap<Int, Color>()
        before.zip(after).forEach { (old, new) -> if (old.rgb != new.rgb) changes.putIfAbsent(old.rgb, new) }
        if (changes.isEmpty()) return
        // Buttons can share one icon. Change each icon one time only, because a new value can also be a key.
        val icons = Collections.newSetFromMap(IdentityHashMap<Icon, Boolean>())
        // Window.getWindows() also returns the owned windows, so the walk does not go into them.
        Window.getWindows().forEach { window ->
            update(window, changes, icons)
            window.repaint()
        }
    }

    private fun update(component: Component, changes: Map<Int, Color>, icons: MutableSet<Icon>) {
        fun swap(color: Color?): Color? = color?.takeIf { it !is UIResource }?.let { changes[it.rgb] }

        if (component.isBackgroundSet) swap(component.background)?.let { component.background = it }
        if (component.isForegroundSet) swap(component.foreground)?.let { component.foreground = it }
        if (component is JComponent) {
            val border = component.border
            val newBorder = swapBorder(border, changes)
            if (newBorder !== border) component.border = newBorder
        }
        when (component) {
            is JTextComponent -> {
                swap(component.caretColor)?.let { component.caretColor = it }
                swap(component.selectionColor)?.let { component.selectionColor = it }
                swap(component.selectedTextColor)?.let { component.selectedTextColor = it }
                swap(component.disabledTextColor)?.let { component.disabledTextColor = it }
            }
            is JTable -> {
                swap(component.gridColor)?.let { component.gridColor = it }
                swap(component.selectionBackground)?.let { component.selectionBackground = it }
                swap(component.selectionForeground)?.let { component.selectionForeground = it }
            }
            is AbstractButton -> listOf(
                component.icon, component.rolloverIcon, component.pressedIcon, component.selectedIcon, component.disabledIcon,
            ).forEach { swapIcon(it, changes, icons) }
            is JLabel -> swapIcon(component.icon, changes, icons)
        }
        if (component is Container) component.components.forEach { update(it, changes, icons) }
    }

    private fun swapIcon(icon: Icon?, changes: Map<Int, Color>, icons: MutableSet<Icon>) {
        if (icon !is FontIcon || !icons.add(icon)) return
        changes[icon.iconColor?.rgb ?: return]?.let { icon.iconColor = it }
    }

    /** Returns [border] with new colors, or [border] itself when no color changes. */
    private fun swapBorder(border: Border?, changes: Map<Int, Color>): Border? = when {
        border is CompoundBorder -> {
            val outside = swapBorder(border.outsideBorder, changes)
            val inside = swapBorder(border.insideBorder, changes)
            if (outside === border.outsideBorder && inside === border.insideBorder) border else CompoundBorder(outside, inside)
        }
        border?.javaClass == LineBorder::class.java -> {
            val line = border as LineBorder
            changes[line.lineColor.rgb]?.let { LineBorder(it, line.thickness, line.roundedCorners) } ?: border
        }
        border?.javaClass == MatteBorder::class.java -> {
            val matte = border as MatteBorder
            val color = matte.matteColor
            if (matte.tileIcon != null || color == null) border
            else changes[color.rgb]?.let { MatteBorder(matte.borderInsets, it) } ?: border
        }
        else -> border
    }
}
