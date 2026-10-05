package org.litvin.ui.commons

import org.litvin.points.CommentStyle
import java.util.prefs.Preferences

/**
 * The style of a new comment: the last style that the user saved in the comment dialog, in any project.
 * The Points tab and the Scoring tab share it.
 */
interface CommentStyleDefaults {
    /** Returns the last saved style, or [CommentStyle.OUTLINE] when the user did not save a style yet. */
    fun load(): CommentStyle

    fun save(style: CommentStyle)

    companion object {
        /** Keeps the style in memory only. Tests and the standalone panels use it. */
        fun inMemory(): CommentStyleDefaults = object : CommentStyleDefaults {
            private var style = CommentStyle.OUTLINE
            override fun load(): CommentStyle = style
            override fun save(style: CommentStyle) {
                this.style = style
            }
        }
    }
}

/** Keeps the default comment style in the user preferences, so it applies to all projects. */
class PreferencesCommentStyleDefaults(private val preferences: Preferences) : CommentStyleDefaults {
    override fun load(): CommentStyle =
        preferences.get(KEY_STYLE, null)?.let { name -> CommentStyle.entries.firstOrNull { it.name == name } }
            ?: CommentStyle.OUTLINE

    override fun save(style: CommentStyle) {
        preferences.put(KEY_STYLE, style.name)
    }

    private companion object {
        const val KEY_STYLE = "default-comment-style"
    }
}
