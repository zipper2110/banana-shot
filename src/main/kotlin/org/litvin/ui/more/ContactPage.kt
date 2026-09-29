package org.litvin.ui.more

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.ui.commons.SectionPage
import java.io.File
import java.net.URI

/**
 * The Contact section of the More window.
 * [onOpenLink] opens the email app, and [onOpenFolder] opens [logFolder] in the file manager.
 */
internal class ContactPage(
    email: String,
    version: String,
    logFolder: File,
    private val onOpenLink: (URI) -> Boolean,
    private val onOpenFolder: (File) -> Boolean,
) : SectionPage(TITLE) {
    init {
        name = "more-contact"
        paragraph("Send questions, problems, and ideas by email.")
        valueRow("Email", email).name = "more-contact-email"
        lateinit var emailStatus: StatusLine
        buttonRow(secondaryButton("Write an email", Material2AL.EDIT) {
            emailStatus.text = if (onOpenLink(URI("mailto:$email"))) "" else "The app cannot open your email app. Copy the address above."
        }.apply { name = "more-contact-write" })
        emailStatus = statusLine()

        subheading("Report a problem")
        paragraph("Tell what you did, what happened, and what you expected. Add the app version and the version of Windows.")
        valueRow("Version", version).name = "more-contact-version"
        paragraph("Attach the log files to the email. The log files help to find the cause of the problem.")
        paragraph(
            "The log files contain the names and folders of your videos and projects. " +
                "They do not contain your videos. Open the files to see all the data before you send them.",
            secondary = true,
        )
        valueRow("Log folder", logFolder.absolutePath).name = "more-contact-log-folder"
        lateinit var folderStatus: StatusLine
        buttonRow(secondaryButton("Open log folder", Material2AL.FOLDER_OPEN) {
            logFolder.mkdirs()
            folderStatus.text = if (onOpenFolder(logFolder)) "" else "The app cannot open the log folder."
        }.apply { name = "more-contact-open-log-folder" })
        folderStatus = statusLine()
    }

    companion object {
        const val TITLE = "Contact"
    }
}
