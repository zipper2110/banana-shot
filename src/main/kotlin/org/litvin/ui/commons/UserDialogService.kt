package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import java.awt.Component

/**
 * The messages and confirmations of the app. The new methods have default bodies, so a fake of the tests
 * needs only the first three methods.
 */
interface UserDialogService {
    fun showInfo(parent: Component?, message: String, title: String = "Info")
    fun showError(parent: Component?, message: String, title: String = "Error")
    fun confirm(parent: Component?, message: String, title: String): Boolean

    /** A message about an action that the app cannot do now. The user did nothing wrong with the data. */
    fun showWarning(parent: Component?, message: String, title: String) = showInfo(parent, message, title)

    /** A short tip about how to use the app. */
    fun showHint(parent: Component?, message: String, title: String = "Hint") = showInfo(parent, message, title)

    /**
     * Asks the user to confirm an action with a button that names the action, for example "Overwrite".
     * For a [destructive] action, the button is red, and the safe button has the focus.
     */
    fun confirm(
        parent: Component?,
        message: String,
        title: String,
        confirmLabel: String,
        cancelLabel: String = "Cancel",
        destructive: Boolean = false,
    ): Boolean = confirm(parent, message, title)
}

class SwingUserDialogService : UserDialogService {
    override fun showInfo(parent: Component?, message: String, title: String) =
        MessageDialog.show(parent, MessageKind.INFO, title, message)

    override fun showError(parent: Component?, message: String, title: String) =
        MessageDialog.show(parent, MessageKind.ERROR, title, message)

    override fun showWarning(parent: Component?, message: String, title: String) =
        MessageDialog.show(parent, MessageKind.WARNING, title, message)

    override fun showHint(parent: Component?, message: String, title: String) =
        MessageDialog.show(parent, MessageKind.HINT, title, message)

    override fun confirm(parent: Component?, message: String, title: String): Boolean =
        MessageDialog.confirm(parent, title, message, confirmLabel = "OK")

    override fun confirm(
        parent: Component?,
        message: String,
        title: String,
        confirmLabel: String,
        cancelLabel: String,
        destructive: Boolean,
    ): Boolean = MessageDialog.confirm(parent, title, message, confirmLabel, cancelLabel, destructive, glyph = glyphFor(confirmLabel, destructive))

    /** A cancel action shows a cancel icon, and an overwrite shows a warning icon. A delete keeps the delete icon. */
    private fun glyphFor(confirmLabel: String, destructive: Boolean): Ikon? = when {
        !destructive -> null
        confirmLabel.startsWith("Cancel") -> Material2AL.CANCEL
        confirmLabel == "Overwrite" -> Material2MZ.WARNING
        else -> null
    }
}
