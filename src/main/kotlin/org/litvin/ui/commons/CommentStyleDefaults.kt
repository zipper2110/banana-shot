package org.litvin.ui.commons

import org.litvin.points.CommentAnimation
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import java.util.prefs.Preferences

/**
 * The look of a new comment: its style, its size, its opacity, and its animation. The percents are in the ranges of
 * [CommentV1].
 */
data class CommentLook(
    val style: CommentStyle = CommentStyle.OUTLINE,
    val sizePercent: Int = CommentV1.DEFAULT_SIZE_PERCENT,
    val opacityPercent: Int = 100,
    val animation: CommentAnimation = CommentAnimation.NONE,
)

/**
 * The look of a new comment: the last style, size, opacity, and animation that the user chose for a comment, in any project.
 * The Points tab uses it.
 */
interface CommentStyleDefaults {
    /** Returns the last saved look, or the default look when the user did not save one yet. */
    fun load(): CommentLook

    fun save(look: CommentLook)

    companion object {
        /** Keeps the look in memory only. Tests and the standalone panels use it. */
        fun inMemory(): CommentStyleDefaults = object : CommentStyleDefaults {
            private var look = CommentLook()
            override fun load(): CommentLook = look
            override fun save(look: CommentLook) {
                this.look = look
            }
        }
    }
}

/** Keeps the look of new comments in the user preferences, so it applies to all projects. */
class PreferencesCommentStyleDefaults(private val preferences: Preferences) : CommentStyleDefaults {
    override fun load(): CommentLook = CommentLook(
        style = preferences.get(KEY_STYLE, null)?.let { name -> CommentStyle.entries.firstOrNull { it.name == name } }
            ?: CommentStyle.OUTLINE,
        sizePercent = preferences.getInt(KEY_SIZE, CommentV1.DEFAULT_SIZE_PERCENT)
            .coerceIn(CommentV1.MIN_SIZE_PERCENT, CommentV1.MAX_SIZE_PERCENT),
        opacityPercent = preferences.getInt(KEY_OPACITY, 100).coerceIn(CommentV1.MIN_OPACITY_PERCENT, 100),
        animation = loadAnimation(),
    )

    private fun loadAnimation(): CommentAnimation =
        preferences.get(KEY_ANIMATION, null)?.let { name -> CommentAnimation.entries.firstOrNull { it.name == name } }
            ?: CommentAnimation.NONE

    override fun save(look: CommentLook) {
        preferences.put(KEY_STYLE, look.style.name)
        preferences.putInt(KEY_SIZE, look.sizePercent)
        preferences.putInt(KEY_OPACITY, look.opacityPercent)
        preferences.put(KEY_ANIMATION, look.animation.name)
    }

    private companion object {
        const val KEY_STYLE = "default-comment-style"
        const val KEY_SIZE = "default-comment-size-percent"
        const val KEY_OPACITY = "default-comment-opacity-percent"
        const val KEY_ANIMATION = "default-comment-animation"
    }
}
