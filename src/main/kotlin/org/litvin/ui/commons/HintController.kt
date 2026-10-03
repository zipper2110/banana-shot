package org.litvin.ui.commons

import javax.swing.JComponent

/**
 * Shows the one-time hints as [HintBalloon]s, one balloon at a time.
 * A hint that comes while another balloon is on the screen waits in a queue. It shows after the other balloon goes,
 * if its anchor is still on the screen. A hint that goes off the screen without a click on its close button
 * (for example, because the user opened a different tab) shows again at its next trigger.
 * Call all functions on the EDT.
 */
class HintController(private val registry: HintRegistry) {
    private class Request(
        val hint: HintId,
        val anchor: JComponent,
        val message: String,
        val placement: HintBalloon.Placement,
        val onClose: () -> Unit,
    )

    private class Shown(val request: Request, val balloon: HintBalloon)

    private var shown: Shown? = null
    private val waiting = ArrayDeque<Request>()

    /** The hint on the screen, or null. */
    val shownHint: HintId?
        get() = shown?.request?.hint

    fun isDismissed(hint: HintId): Boolean = registry.isDismissed(hint)

    /**
     * Shows [hint] next to [anchor], or puts it in the queue while another balloon is on the screen.
     * Does nothing for a closed hint. A hint that is already on the screen at a different anchor moves to [anchor].
     * [onClose] runs after the user clicks the close button.
     */
    fun show(
        hint: HintId,
        anchor: JComponent,
        message: String,
        placement: HintBalloon.Placement = HintBalloon.Placement.RIGHT,
        onClose: () -> Unit = {},
    ) {
        if (registry.isDismissed(hint)) return
        val request = Request(hint, anchor, message, placement, onClose)
        val current = shown
        if (current != null) {
            if (current.request.hint != hint) {
                waiting.removeAll { it.hint == hint }
                waiting.addLast(request)
                return
            }
            if (current.request.anchor === anchor) return
            // The anchor changed, for example because the rows were built again. Move the balloon before the queue.
            shown = null
            current.balloon.hideBalloon()
        }
        if (!display(request)) showNext()
    }

    /** The user did the action that the hint teaches. The hint goes off the screen and never shows again. */
    fun dismiss(hint: HintId) {
        if (registry.isDismissed(hint)) return
        registry.dismiss(hint)
        hide(hint)
    }

    /** Takes the hint off the screen and out of the queue. It can show again at its next trigger. */
    fun hide(hint: HintId) {
        waiting.removeAll { it.hint == hint }
        val current = shown ?: return
        if (current.request.hint == hint) current.balloon.hideBalloon()
    }

    /** Makes all hints show again. */
    fun resetAll() = registry.resetAll()

    private fun display(request: Request): Boolean {
        if (!request.anchor.isShowing) return false
        val balloon = HintBalloon(request.message, request.placement) {
            registry.dismiss(request.hint)
            request.onClose()
        }
        val entry = Shown(request, balloon)
        balloon.onHidden = {
            if (shown === entry) {
                shown = null
                showNext()
            }
        }
        shown = entry
        balloon.showAt(request.anchor)
        if (balloon.isShown) return true
        if (shown === entry) shown = null
        return false
    }

    private fun showNext() {
        while (shown == null) {
            val next = waiting.removeFirstOrNull() ?: return
            if (!registry.isDismissed(next.hint)) display(next)
        }
    }

    companion object {
        /** Never shows a hint. Tests and the standalone panels use it. */
        val NONE = HintController(HintRegistry.NONE)
    }
}
