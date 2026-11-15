package org.litvin.ui.commons

import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * A click: a press and a release of the same button on the same component, with the release inside the component.
 * Use it in place of mouseClicked. Swing sends no mouseClicked when the pointer moves between the press and the release.
 * On Windows and on a trackpad, the pointer often moves 1 pixel, and then many clicks do nothing.
 * [onClick] gets the release event. For the second click of a double-click, its click count is 2.
 */
class ClickListener(private val onClick: (MouseEvent) -> Unit) : MouseAdapter() {
    /** The component of the last press, until the release. */
    private var pressed: Component? = null
    private var button = MouseEvent.NOBUTTON

    override fun mousePressed(e: MouseEvent) {
        pressed = e.component
        button = e.button
    }

    override fun mouseReleased(e: MouseEvent) {
        val source = pressed
        pressed = null
        // Swing sends the release to the component of the press, also when the pointer is outside it.
        if (source !== e.component || e.button != button || !e.component.contains(e.point)) return
        onClick(e)
    }
}
