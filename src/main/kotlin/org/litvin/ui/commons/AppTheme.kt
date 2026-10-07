package org.litvin.ui.commons

import java.awt.Color
import java.util.prefs.Preferences

/** A theme that the user can select in More > Settings. Each theme sets the seed colors of [Palette]. */
enum class AppTheme(val id: String, val title: String, val accent: Color, val background: Color) {
    DARK("dark", "Dark", Palette.DEFAULT_ACCENT, Palette.DEFAULT_BACKGROUND),
    MID("mid", "Mid", Palette.DEFAULT_ACCENT, Palette.MID_BACKGROUND),
    LIGHT("light", "Light", Palette.LIGHT_ACCENT, Palette.LIGHT_BACKGROUND),
    ;

    companion object {
        val DEFAULT = DARK

        /** The theme with [id], or [DEFAULT] for an unknown id. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The saved theme choice and the custom accent, in the application preferences. */
internal class ThemePreferences(private val preferences: Preferences) {
    fun load(): AppTheme = AppTheme.fromId(preferences.get(THEME_KEY, null))

    fun save(theme: AppTheme) {
        preferences.put(THEME_KEY, theme.id)
    }

    /** The custom accent, or null when each theme uses its own accent. */
    fun loadAccent(): Color? = preferences.get(ACCENT_KEY, null)?.let(ColorPickerDialog::parseHex)

    fun saveAccent(accent: Color?) {
        if (accent == null) preferences.remove(ACCENT_KEY) else preferences.put(ACCENT_KEY, Palette.hex(Color(accent.rgb and 0xFFFFFF)))
    }

    /** The accent seed for [theme]: the custom accent, or the accent of the theme. */
    fun accentFor(theme: AppTheme): Color = loadAccent() ?: theme.accent

    private companion object {
        const val THEME_KEY = "ui.theme"
        const val ACCENT_KEY = "ui.accent"
    }
}

/** The theme settings of More > Settings. */
interface ThemeSettings {
    val theme: AppTheme

    /** The custom accent, or null when the theme uses its own accent. */
    val customAccent: Color?

    fun selectTheme(theme: AppTheme)

    /** Saves and applies [accent]. Null removes the custom accent. */
    fun selectAccent(accent: Color?)

    /** Applies [accent] to the open windows and does not save it, for the live preview of the color picker. */
    fun previewAccent(accent: Color)

    /** Calls [listener] after the user selects a theme or an accent, so that each theme control can show it. */
    fun onChange(listener: () -> Unit)
}

/** Saves the theme settings in [preferences] and applies them to the open windows. */
internal class ThemeController(private val preferences: ThemePreferences) : ThemeSettings {
    private val listeners = mutableListOf<() -> Unit>()

    override var theme: AppTheme = preferences.load()
        private set

    override var customAccent: Color? = preferences.loadAccent()
        private set

    override fun selectTheme(theme: AppTheme) {
        this.theme = theme
        preferences.save(theme)
        Theme.apply(theme, customAccent ?: theme.accent)
        listeners.toList().forEach { it() }
    }

    override fun selectAccent(accent: Color?) {
        customAccent = accent
        preferences.saveAccent(accent)
        Theme.apply(theme, accent ?: theme.accent)
        listeners.toList().forEach { it() }
    }

    override fun previewAccent(accent: Color) = Theme.apply(theme, accent)

    override fun onChange(listener: () -> Unit) {
        listeners += listener
    }
}
