package org.litvin.ui.privacy

import org.kordamp.ikonli.feather.Feather
import org.litvin.AppInfo
import org.litvin.ui.commons.AuthorPortrait
import org.litvin.ui.commons.MessageDialog
import java.awt.Component
import org.litvin.i18n.tr

/**
 * The request from the app author to keep the essential statistics on. It has the same look as the request to keep
 * the app credit line on the scoreboard: the photo of the author, or a heart when the photo is missing. Escape and the close button turn the statistics off, so the choice of the
 * user stays the default answer.
 */
object KeepStatisticsRequest {
    internal val TITLE: String get() = tr("keepStats.title")
    internal val TEXT: String get() = tr("keepStats.text", "app" to AppInfo.NAME)
    internal val KEEP_BUTTON: String get() = tr("keepStats.keep")
    internal val OFF_BUTTON: String get() = tr("keepStats.off")

    /** Shows the request and waits. Returns true when the user keeps the statistics on. */
    fun ask(parent: Component): Boolean = MessageDialog.confirm(
        parent, TITLE, TEXT, confirmLabel = KEEP_BUTTON, cancelLabel = OFF_BUTTON, glyph = Feather.HEART,
        portrait = AuthorPortrait.image,
    )
}
