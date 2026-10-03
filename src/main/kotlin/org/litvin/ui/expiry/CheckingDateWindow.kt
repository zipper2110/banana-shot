package org.litvin.ui.expiry

import org.litvin.AppInfo
import org.litvin.ui.commons.AppIcon
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import javax.swing.Box
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * The "Checking the date…" window of path A (E8-S6, build-expiry-spec.md "Start of a build that looks expired").
 * It shows before the main window. Thus, the user cannot open a project during the check.
 */
object CheckingDateWindow {
    /**
     * Shows the window, runs [check] on the calling thread, and closes the window. Call it on a thread that is not
     * the EDT, because [check] can take 15 seconds. With no screen (headless), it only runs [check].
     */
    fun <T> during(check: () -> T): T {
        if (GraphicsEnvironment.isHeadless() || EventQueue.isDispatchThread()) return check()
        var window: JDialog? = null
        EventQueue.invokeAndWait { window = build().apply { isVisible = true } }
        try {
            return check()
        } finally {
            EventQueue.invokeLater { window?.dispose() }
        }
    }

    internal fun build(): JDialog = JDialog(null as JFrame?, AppInfo.NAME).apply {
        name = "checking-date-window"
        iconImages = AppIcon.windowImages()
        // The user cannot stop the check. The window closes after a maximum of 15 seconds.
        defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        isResizable = false
        contentPane = MessageDialog.content(
            MessageKind.INFO,
            ExpiryTexts.CHECKING_DATE_TITLE,
            listOf(MessageDialog.paragraph(ExpiryTexts.CHECKING_DATE)),
            Box.createVerticalStrut(0) as javax.swing.JComponent,
        )
        pack()
        setLocationRelativeTo(null)
    }
}
