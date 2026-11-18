package org.litvin.ui.commons

import org.litvin.points.CommentAnimation
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.points.Replays
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

    /** Returns the last time of a new replay comment, or [DEFAULT_REPLAY_COMMENT_MS] when the user did not save one yet. */
    fun loadReplayDurationMs(): Long

    fun saveReplayDurationMs(durationMs: Long)

    companion object {
        /** The time of a new replay comment before the user saves another time. */
        const val DEFAULT_REPLAY_COMMENT_MS = 3_000L

        /** Keeps the look in memory only. Tests and the standalone panels use it. */
        fun inMemory(): CommentStyleDefaults = object : CommentStyleDefaults {
            private var look = CommentLook()
            private var replayDurationMs = DEFAULT_REPLAY_COMMENT_MS
            override fun load(): CommentLook = look
            override fun save(look: CommentLook) {
                this.look = look
            }
            override fun loadReplayDurationMs(): Long = replayDurationMs
            override fun saveReplayDurationMs(durationMs: Long) {
                replayDurationMs = durationMs
            }
        }
    }
}

/** Keeps the look of new comments and the time of new replay comments in the user preferences, so they apply to all projects. */
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

    override fun loadReplayDurationMs(): Long =
        preferences.getLong(KEY_REPLAY_DURATION, CommentStyleDefaults.DEFAULT_REPLAY_COMMENT_MS)
            .coerceIn(Replays.MIN_COMMENT_MS.toLong(), Replays.MAX_COMMENT_MS.toLong())

    override fun saveReplayDurationMs(durationMs: Long) {
        preferences.putLong(KEY_REPLAY_DURATION, durationMs)
    }

    private companion object {
        const val KEY_STYLE = "default-comment-style"
        const val KEY_SIZE = "default-comment-size-percent"
        const val KEY_OPACITY = "default-comment-opacity-percent"
        const val KEY_ANIMATION = "default-comment-animation"
        const val KEY_REPLAY_DURATION = "default-replay-comment-duration-ms"
    }
}
