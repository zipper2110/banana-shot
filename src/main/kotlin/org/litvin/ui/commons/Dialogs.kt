package org.litvin.ui.commons

import java.awt.Component

/**
 * Dialog helpers for consistent error/info reporting across Swing UI.
 * They show the message box of the app ([MessageDialog]).
 *
 * Preferred entry points:
 * - {@link #showError} for exceptions and error messages
 * - {@link #showInfo} for informational messages
 *
 * Migration note (v0.3.0): moved from `org.litvin.SwingDialogUtils` and renamed to `Dialogs`.
 */
object Dialogs {
    fun showError(parent: Component?, throwable: Throwable, title: String = "Error") {
        MessageDialog.show(parent, MessageKind.ERROR, title, throwable.message ?: throwable.toString())
    }

    fun showInfo(parent: Component?, message: String, title: String = "Info") {
        MessageDialog.show(parent, MessageKind.INFO, title, message)
    }
}
