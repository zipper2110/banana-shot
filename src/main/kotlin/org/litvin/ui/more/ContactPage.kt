package org.litvin.ui.more

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.SectionPage
import java.io.File
import java.net.URI
import org.litvin.i18n.tr

/**
 * The Contact section of the More window.
 * [onSendFeedback] opens the feedback form (T3 of B-8). It is the first action. The email address is the second way.
 * [onOpenLink] opens the email app, and [onOpenFolder] opens [logFolder] in the file manager.
 */
internal class ContactPage(
    email: String,
    version: String,
    logFolder: File,
    private val onSendFeedback: () -> Unit,
    private val onOpenLink: (URI) -> Boolean,
    private val onOpenFolder: (File) -> Boolean,
) : SectionPage(TITLE) {
    init {
        name = "more-contact"
        paragraph(tr("contact.text"))
        buttonRow(secondaryButton(tr("contact.sendFeedback"), Material2MZ.SEND) { onSendFeedback() }.apply { name = "more-contact-feedback" })
        paragraph(tr("contact.reply"), secondary = true)

        subheading(tr("contact.email"))
        paragraph(tr("contact.email.text"))
        valueRow(tr("contact.email.label"), email).name = "more-contact-email"
        lateinit var emailStatus: StatusLine
        buttonRow(secondaryButton(tr("contact.writeEmail"), Material2AL.EDIT) {
            emailStatus.text = if (onOpenLink(URI("mailto:$email"))) "" else tr("contact.writeEmail.failed")
        }.apply { name = "more-contact-write" })
        emailStatus = statusLine()

        subheading(tr("contact.report"))
        paragraph(tr("contact.report.text"))
        valueRow(tr("about.version"), version).name = "more-contact-version"
        paragraph(tr("contact.report.attach"))
        paragraph(tr("contact.report.logContents"), secondary = true)
        valueRow(tr("contact.logFolder"), logFolder.absolutePath).name = "more-contact-log-folder"
        lateinit var folderStatus: StatusLine
        buttonRow(secondaryButton(tr("contact.openLogFolder"), Material2AL.FOLDER_OPEN) {
            logFolder.mkdirs()
            folderStatus.text = if (onOpenFolder(logFolder)) "" else tr("contact.openLogFolder.failed")
        }.apply { name = "more-contact-open-log-folder" })
        folderStatus = statusLine()
    }

    companion object {
        const val ID = "contact"
        val TITLE: String get() = tr("more.contact")
    }
}
