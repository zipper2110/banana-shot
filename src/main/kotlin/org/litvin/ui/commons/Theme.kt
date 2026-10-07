package org.litvin.ui.commons

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * The runtime theme. [start] sets the theme before the app makes its windows.
 * [apply] changes the theme of the open windows at once.
 *
 * The tokens of [Palette] are live colors, so a component that keeps a token gets the new value at its next repaint.
 * Thus, keep the token itself: do not copy its value into a new [Color], a cached image or a string.
 * A FlatLaf style string must come from [themedStyle], so that [apply] can make it again.
 */
internal object Theme {
    private const val STYLE_BUILDER = "bananashot.themedStyle"

    /** Sets the seeds and the look and feel of [theme]. Call it one time, before the app makes a component. */
    fun start(theme: AppTheme, accent: Color = theme.accent) {
        Palette.setSeeds(accent, theme.background)
        UIManager.setLookAndFeel(lookAndFeel())
    }

    /** Changes the open windows to [theme] with the accent seed [accent]. */
    fun apply(theme: AppTheme, accent: Color = theme.accent) = apply(accent, theme.background)

    fun apply(accent: Color, background: Color) {
        if (accent.rgb == Palette.accent.rgb && background.rgb == Palette.background.rgb) return
        Palette.setSeeds(accent, background)
        val laf = lookAndFeel()
        val lafChanged = UIManager.getLookAndFeel()?.javaClass != laf.javaClass
        if (lafChanged) UIManager.setLookAndFeel(laf)
        // Window.getWindows() also returns the owned windows, so the walk does not go into them.
        Window.getWindows().forEach { window ->
            update(window, lafChanged)
            window.invalidate()
            window.validate()
            window.repaint()
        }
    }

    /**
     * Sets the FlatLaf style of [component] from [build]. [apply] calls [build] again after a theme change,
     * because a style string keeps the color values of the time when it was made.
     */
    fun themedStyle(component: JComponent, build: () -> String) {
        component.putClientProperty(STYLE_BUILDER, build)
        component.putClientProperty(FlatClientProperties.STYLE, build())
    }

    private fun lookAndFeel(): FlatLaf = if (Palette.isLightBackground) FlatLightLaf() else FlatDarkLaf()

    /**
     * Updates [component] and its children. A new look and feel needs new UI delegates. A component with a UI delegate
     * of the app (a custom slider or scroll bar) keeps its delegate: that delegate paints with the live Palette colors.
     */
    private fun update(component: Component, lafChanged: Boolean) {
        if (component is JComponent) {
            if (lafChanged && !hasAppUi(component)) component.updateUI()
            @Suppress("UNCHECKED_CAST")
            (component.getClientProperty(STYLE_BUILDER) as? () -> String)?.let {
                component.putClientProperty(FlatClientProperties.STYLE, it())
            }
            if (lafChanged) component.componentPopupMenu?.let { SwingUtilities.updateComponentTreeUI(it) }
        }
        if (component is Container) component.components.forEach { update(it, lafChanged) }
    }

    private fun hasAppUi(component: JComponent): Boolean =
        component.ui?.javaClass?.name?.startsWith("org.litvin.") == true
}
