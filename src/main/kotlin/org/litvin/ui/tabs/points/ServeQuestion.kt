package org.litvin.ui.tabs.points

/**
 * The "Who serves?" question of advanced mode (B-68, "The server"). Advanced mode needs the server of each point. When
 * the project has no serve mark, the point bar asks the question:
 * - when the user selects advanced mode,
 * - when the user presses C in advanced mode.
 *
 * The answer becomes the serve mark of the pending point or of the selected point. When there is no such point, the
 * question keeps the answer, and the next C gives it to the new point.
 */
internal class ServeQuestion {
    /** True while the point bar shows the question. The ending buttons are not active until the user answers. */
    var isOpen: Boolean = false
        private set

    // The answer that waits for the next C.
    private var kept: Int? = null

    /** The user selected a mode. [serverKnown] is true when a point or the pending point has a serve mark. */
    fun onModeSelected(advanced: Boolean, serverKnown: Boolean) {
        isOpen = advanced && !serverKnown
        kept = null
    }

    /** C started a point. Returns the kept answer (1 or 2), which becomes the serve mark of the new point, or null. */
    fun onPointStart(advanced: Boolean, serverKnown: Boolean): Int? {
        if (!advanced || serverKnown) {
            close()
            return null
        }
        val answer = kept
        kept = null
        isOpen = answer == null
        return answer
    }

    /**
     * The user answered [player] (1 or 2). Returns the serve mark for [target], the id of the pending point or of the
     * selected point. With no [target], the question keeps the answer for the next C and returns null.
     */
    fun answer(player: Int, target: String?): Pair<String, Int>? {
        isOpen = false
        if (target == null) {
            kept = player
            return null
        }
        kept = null
        return target to player
    }

    /** Closes the question when the project does not need it: basic mode, or a known server. */
    fun sync(advanced: Boolean, serverKnown: Boolean) {
        if (!advanced || serverKnown) close()
    }

    /** Closes the question and forgets a kept answer, for example when another project opens. */
    fun close() {
        isOpen = false
        kept = null
    }
}
