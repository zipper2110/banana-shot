package org.litvin.ui.more

import org.litvin.ui.commons.SectionPage
import java.net.URI
import javax.swing.JTextArea

/** The Contact section of the More window. */
class ContactPage(
    email: String,
    version: String,
    private val onOpenLink: (URI) -> Boolean,
) : SectionPage(TITLE) {
    init {
        name = "more-contact"
        paragraph("Send questions, problems, and ideas by email.")
        valueRow("Email", email).name = "more-contact-email"
        lateinit var emailStatus: JTextArea
        buttonRow(secondaryButton("Write an email") {
            emailStatus.text = if (onOpenLink(URI("mailto:$email"))) "" else "The app cannot open your email app. Copy the address above."
        }.apply { name = "more-contact-write" })
        emailStatus = statusLine()

        subheading("Report a problem")
        paragraph("Tell what you did, what happened, and what you expected. Add the app version and the version of Windows.")
        valueRow("Version", version).name = "more-contact-version"
    }

    companion object {
        const val TITLE = "Contact"
    }
}
