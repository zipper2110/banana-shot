package org.litvin.ui.tabs.points

import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.ui.commons.AppShortcut
import org.litvin.ui.commons.AppShortcuts

/** What a digit key does while the error kind question can be open (B-68, "Follow-up questions"). */
internal sealed interface ErrorKindKey {
    /** The question is closed: the key selects an ending, as usual. */
    data object SelectsEnding : ErrorKindKey

    /** The question is open, and the key is not an answer: the key does nothing. */
    data object Ignored : ErrorKindKey

    /** The question is open, and the key gives [kind] to the clip with [pointId]. The question closes. */
    data class Answer(val pointId: String, val kind: ErrorKind) : ErrorKindKey
}

/**
 * The error kind question of advanced mode (B-68, "Follow-up questions"). The project option
 * [org.litvin.scoring.ScoreV1.askErrorKind] turns it on.
 * - An error ending (a forced or an unforced error) on a clip opens the question for that clip.
 * - While the question is open, the digit keys answer it ([keyOf]) and do not select an ending.
 * - C, R, and Esc close the question without an answer. Esc does not cancel the pending point.
 * - The strip under the point bar shows the question while it is open. When it is closed, the strip shows for the
 *   active clip with an error ending ([stripClip]). A click on the strip opens the question again.
 */
internal class ErrorKindQuestion {
    /** True while the strip asks the question. Then the digit keys answer it. */
    var isOpen: Boolean = false
        private set

    /** The clip that the open question asks about, or null when the question is closed. */
    var target: String? = null
        private set

    /**
     * An ending key or button gave [ending] to the clip with [pointId]. The question opens when [asks] is true (advanced
     * mode with the option on) and the ending is an error. Else it closes.
     */
    fun onEnding(asks: Boolean, pointId: String, ending: Ending?) {
        if (asks && ending?.isError == true) open(pointId) else close()
    }

    /** A click on the strip of the clip with [pointId] opens the question again. */
    fun open(pointId: String) {
        isOpen = true
        target = pointId
    }

    /** A digit key with [key]. See [ErrorKindKey]. */
    fun onKey(key: AppShortcut): ErrorKindKey {
        val pointId = target
        if (!isOpen || pointId == null) return ErrorKindKey.SelectsEnding
        val kind = kindOf(key) ?: return ErrorKindKey.Ignored
        close()
        return ErrorKindKey.Answer(pointId, kind)
    }

    /** Esc. Returns true when Esc closed the question. Then Esc does nothing more: it does not cancel the pending point. */
    fun onEscape(): Boolean {
        if (!isOpen) return false
        close()
        return true
    }

    /**
     * Closes the question when it does not apply any more:
     * - [asks] is false (basic mode, or the option is off),
     * - the target clip has no error ending now ([targetIsError] is false), for example after Q, E, or the Edit dialog,
     * - another clip is active now ([activeId]). No active clip keeps the question open, so that the video can play on
     *   after the end of the point.
     */
    fun sync(asks: Boolean, targetIsError: Boolean, activeId: String?) {
        if (!isOpen) return
        if (!asks || !targetIsError || (activeId != null && activeId != target)) close()
    }

    fun close() {
        isOpen = false
        target = null
    }

    /**
     * The clip of the strip, or null when the strip does not show. The strip shows the open question. When the question
     * is closed, it shows for the active clip ([activeId]) when [asks] is true and the clip has an error ending
     * ([activeIsError]).
     */
    fun stripClip(asks: Boolean, activeId: String?, activeIsError: Boolean): String? = when {
        isOpen -> target
        asks && activeIsError -> activeId
        else -> null
    }

    companion object {
        /** The answers in the order of the strip. */
        val ANSWERS: List<ErrorKind> = listOf(ErrorKind.NET, ErrorKind.LONG, ErrorKind.WIDE)

        /** The digit key of each answer: 1 net, 2 long, 3 wide. They are the keys of the first ending buttons. */
        fun keyOf(kind: ErrorKind): AppShortcut = when (kind) {
            ErrorKind.NET -> AppShortcuts.ENDING_P1_WINNER
            ErrorKind.LONG -> AppShortcuts.ENDING_P2_FORCED_ERROR
            ErrorKind.WIDE -> AppShortcuts.ENDING_P2_UNFORCED_ERROR
        }

        /** The answer of the digit key [key], or null when the key is not an answer. */
        fun kindOf(key: AppShortcut): ErrorKind? = ANSWERS.firstOrNull { keyOf(it) == key }
    }
}
