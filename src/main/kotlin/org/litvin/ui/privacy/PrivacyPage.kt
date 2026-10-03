package org.litvin.ui.privacy

import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsPreferences
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.SectionPage
import org.litvin.ui.commons.SwitchBox
import java.net.URI

/** The Privacy section of the More window. */
internal class PrivacyPage private constructor() : SectionPage(TITLE) {
    init {
        name = "more-privacy"
    }

    /**
     * What a feedback report contains, where it goes, how long the server keeps it, and how to ask for its deletion
     * (T4 of B-8). The analytics choice does not change the feedback.
     */
    private fun feedbackSection(contactEmail: String) {
        subheading("Feedback reports")
        paragraph(
            "The app sends a report only when you click Send in the feedback form. " +
                "The usage analytics choice does not change this.",
        ).name = "more-privacy-feedback"
        paragraph(
            "A report contains the topic, your message, the app version, the version of Windows, and the Java version. " +
                "It also contains your email address if you give it, the error text if you send the report from an error " +
                "message, and the end of the log files if you select \"Attach the log files\". The log files contain " +
                "the names and folders of your videos and projects, but not the videos.",
        )
        paragraph(
            "The report goes to our server on Cloudflare. The server sends it to the author through Telegram. " +
                "The server keeps the report text for 90 days. It does not keep the log files or your IP address. " +
                "The copy in the Telegram chat of the author stays until the author deletes it.",
            secondary = true,
        )
        paragraph("To ask for the deletion of a report, write to $contactEmail and give the report ID.", secondary = true)
    }

    companion object {
        const val TITLE = "Privacy"

        /** The page for a build that can send usage analytics. The user can turn the analytics on and off. */
        fun withAnalytics(
            controller: AnalyticsController,
            preferences: AnalyticsPreferences,
            privacyUrl: URI,
            contactEmail: String,
            linkOpener: PrivacyLinkOpener = PrivacyLinkOpener.DesktopBrowser,
        ): PrivacyPage = PrivacyPage().apply {
            subheading("Usage analytics")
            val toggle = SwitchBox("Send optional usage analytics").apply {
                isSelected = preferences.resolve().isEnabled
                name = "more-privacy-analytics"
                addActionListener { if (isSelected) controller.enable() else controller.disable() }
            }
            addItem(toggle)
            paragraph("Collected: approved product action categories only.")
            paragraph(
                "Excluded: video, audio, filenames, paths, project data, scores, player data, identifiers, and diagnostics.",
                secondary = true,
            )
            buttonRow(
                secondaryButton("Privacy notice", Material2MZ.OPEN_IN_NEW) { linkOpener.open(privacyUrl) },
                secondaryButton("Contact", Material2MZ.MAIL_OUTLINE) { linkOpener.open(URI("mailto:$contactEmail")) },
            )
            versionCheck()
            feedbackSection(contactEmail)
        }

        /** The page for a build that cannot send usage analytics. */
        fun withoutAnalytics(contactEmail: String): PrivacyPage = PrivacyPage().apply {
            subheading("Usage analytics")
            paragraph("This version of the app does not send usage analytics. Your videos and projects stay on this computer.")
            versionCheck()
            feedbackSection(contactEmail)
        }

        /**
         * The requests to GitHub for the build expiry and the update (build-expiry-spec.md, "Privacy"). They are not
         * part of the analytics, so they have their own section in each build.
         */
        private fun PrivacyPage.versionCheck() {
            subheading(VERSION_CHECK_TITLE)
            VERSION_CHECK_TEXT.forEach { paragraph(it) }
        }

        const val VERSION_CHECK_TITLE = "Version check and updates"

        val VERSION_CHECK_TEXT = listOf(
            "${AppInfo.NAME} reads a small file from GitHub when it starts and from time to time while it runs. " +
                "The file tells if this version still works and if a new version is available.",
            "The request sends no user ID and no analytics data. GitHub receives your IP address and the standard " +
                "data of a web request.",
            "\"Update and restart\" downloads the setup file of the new version from GitHub. This request also sends " +
                "no user ID.",
            "The version check is necessary for the license, so you cannot turn it off. It is not part of the usage analytics.",
        )
    }
}
