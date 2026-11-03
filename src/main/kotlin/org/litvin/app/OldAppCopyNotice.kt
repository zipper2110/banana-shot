package org.litvin.app

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.AppInfo
import org.litvin.i18n.tr
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import java.awt.Component
import java.awt.Desktop
import java.awt.EventQueue
import java.io.File

/**
 * Offers to move the old copy of the app to the Trash after an update on macOS (B-13, step 11). See [OldAppCopy].
 */
internal object OldAppCopyNotice {
    private val logger = KotlinLogging.logger {}

    /** Asks the user when [copy] finds an old copy. Call it on the event dispatch thread after the main window shows. */
    fun offer(parent: Component, copy: OldAppCopy, currentFolder: File?) {
        val old = copy.leftOver() ?: return
        val keep = !MessageDialog.confirm(
            parent,
            tr("update.oldCopy.title", "app" to AppInfo.NAME),
            tr(
                "update.oldCopy.text",
                "app" to AppInfo.NAME,
                "version" to AppInfo.version,
                "folder" to (currentFolder?.path ?: ""),
                "oldVersion" to old.version.toString(),
                "path" to old.bundle.path,
            ),
            confirmLabel = tr("update.oldCopy.trash"),
            cancelLabel = tr("update.oldCopy.keep"),
        )
        if (keep) {
            logger.info { "The user keeps the old copy ${old.bundle}." }
            return
        }
        // macOS moves the file on its main thread. The event dispatch thread must not wait for it.
        Thread({
            val moved = runCatching {
                Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
                    Desktop.getDesktop().moveToTrash(old.bundle)
            }.onFailure { logger.warn(it) { "Cannot move ${old.bundle} to the Trash." } }.getOrDefault(false)
            logger.info { if (moved) "Moved the old copy ${old.bundle} to the Trash." else "The old copy ${old.bundle} is still there." }
            if (!moved) {
                EventQueue.invokeLater {
                    MessageDialog.show(
                        parent,
                        MessageKind.ERROR,
                        tr("update.oldCopy.title", "app" to AppInfo.NAME),
                        tr("update.oldCopy.failed", "path" to old.bundle.path),
                    )
                }
            }
        }, "bananashot-old-copy").apply { isDaemon = true }.start()
    }
}
