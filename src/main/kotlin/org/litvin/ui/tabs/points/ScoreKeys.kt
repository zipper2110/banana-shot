package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.ui.commons.HintId

/** What the Points tab does for a scoring key (B-68, "Serve faults and lets" and "Corrections"). */
internal enum class ScoreKeyAction {
    /** The key ends the pending point at the playhead and gives the new clip its outcome. */
    END_PENDING,

    /** The key changes the outcome of the active clip. */
    CHANGE_ACTIVE,

    /** X adds a fault clip of zero length at the playhead. */
    ADD_ZERO_LENGTH_FAULT,

    /** The key does nothing and shows a toast. */
    HINT,

    /** The key does nothing. */
    NONE,
}

/**
 * The hints of the scoring keys that do nothing (B-68, "Hints"). Each hint is a toast with "Don't show again". To add a
 * hint for another key, add an entry here and an entry in [HintId].
 */
internal enum class KeyHint(val id: HintId) {
    /** L with no pending point and no active clip. */
    LET_WITHOUT_POINT(HintId.LET_WITHOUT_POINT),

    /** X with no pending point and no active clip, in a pack that already has a fault clip or a let clip. */
    FAULT_IN_SERVE_PACK(HintId.FAULT_IN_SERVE_PACK),
    ;

    val message: String
        get() = when (this) {
            LET_WITHOUT_POINT -> tr("points.hint.letWithoutPoint")
            FAULT_IN_SERVE_PACK -> tr("points.hint.faultInServePack")
        }
}

/** The decision for a scoring key: what it does, and the hint when it does nothing. */
internal data class ScoreKeyDecision(val action: ScoreKeyAction, val hint: KeyHint? = null)

/** The rules of the scoring keys with and without a pending point (B-68). */
internal object ScoreKeys {
    /**
     * The decision for [request].
     * - With a pending point, each key ends it.
     * - With no pending point and an active clip ([hasActiveClip]), each key changes that clip.
     * - With no pending point and no active clip, X adds a fault clip of zero length at the playhead. When the pack at
     *   the playhead already has a fault clip or a let clip ([packHasServeClip]), X adds nothing and shows a hint. L
     *   shows a hint. The other keys do nothing.
     */
    fun decide(request: ScoreRequest, pending: Boolean, hasActiveClip: Boolean, packHasServeClip: () -> Boolean): ScoreKeyDecision =
        when {
            pending -> ScoreKeyDecision(ScoreKeyAction.END_PENDING)
            hasActiveClip -> ScoreKeyDecision(ScoreKeyAction.CHANGE_ACTIVE)
            request is ScoreRequest.Fault -> if (packHasServeClip()) {
                ScoreKeyDecision(ScoreKeyAction.HINT, KeyHint.FAULT_IN_SERVE_PACK)
            } else {
                ScoreKeyDecision(ScoreKeyAction.ADD_ZERO_LENGTH_FAULT)
            }
            request is ScoreRequest.Let -> ScoreKeyDecision(ScoreKeyAction.HINT, KeyHint.LET_WITHOUT_POINT)
            else -> ScoreKeyDecision(ScoreKeyAction.NONE)
        }
}
