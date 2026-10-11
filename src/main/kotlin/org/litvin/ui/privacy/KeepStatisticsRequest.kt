package org.litvin.ui.privacy

import org.kordamp.ikonli.feather.Feather
import org.litvin.AppInfo
import org.litvin.ui.commons.AuthorPortrait
import org.litvin.ui.commons.MessageDialog
import java.awt.Component

/**
 * The request from the app author to keep the essential statistics on. It has the same look as the request to keep
 * the app credit line on the scoreboard: the photo of the author, or a heart when the photo is missing. Escape and the close button turn the statistics off, so the choice of the
 * user stays the default answer.
 */
object KeepStatisticsRequest {
    internal const val TITLE = "A small favor"
    internal val TEXT = "Hey there! I make ${AppInfo.NAME} in my free time. The essential statistics are anonymous: " +
        "no videos, names, scores, or files. They only show me how many people use the app and if it crashes. " +
        "Could you keep them on? Thanks! ;)"
    internal const val KEEP_BUTTON = "Keep them on"
    internal const val OFF_BUTTON = "Turn them off"

    /** Shows the request and waits. Returns true when the user keeps the statistics on. */
    fun ask(parent: Component): Boolean = MessageDialog.confirm(
        parent, TITLE, TEXT, confirmLabel = KEEP_BUTTON, cancelLabel = OFF_BUTTON, glyph = Feather.HEART,
        portrait = AuthorPortrait.image,
    )
}
