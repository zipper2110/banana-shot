package org.litvin.ui.privacy

import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsLevel
import org.litvin.analytics.AnalyticsPreferences.Choice
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
                "The usage statistics choice does not change this.",
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
        paragraph(
            "To ask for the deletion of a report, write to $contactEmail. Give the report ID, or the date and " +
                "the text of your message.",
            secondary = true,
        )
    }

    companion object {
        const val TITLE = "Privacy"

        /**
         * The page for a build that sends usage statistics. One switch turns all statistics on and off. The other
         * switch selects the extended statistics. It works only while the essential statistics are on.
         */
        fun withAnalytics(
            controller: AnalyticsController,
            preferences: AnalyticsPreferences,
            privacyUrl: URI,
            contactEmail: String,
            linkOpener: PrivacyLinkOpener = PrivacyLinkOpener.DesktopBrowser,
        ): PrivacyPage = PrivacyPage().apply {
            subheading(ANALYTICS_TITLE)
            val level = preferences.resolve().level
            val essential = SwitchBox(ESSENTIAL_SWITCH).apply {
                isSelected = level != null
                name = "more-privacy-essential"
            }
            val extended = SwitchBox(EXTENDED_SWITCH).apply {
                isSelected = level == AnalyticsLevel.EXTENDED
                isEnabled = level != null
                name = "more-privacy-extended"
                addActionListener { controller.choose(if (isSelected) Choice.EXTENDED else Choice.ESSENTIAL) }
            }
            essential.addActionListener {
                extended.isEnabled = essential.isSelected
                if (!essential.isSelected) extended.isSelected = false
                controller.choose(if (essential.isSelected) Choice.ESSENTIAL else Choice.OFF)
            }
            addItem(essential)
            paragraph(ANALYTICS_ESSENTIAL)
            addItem(extended)
            paragraph(ANALYTICS_EXTENDED)
            paragraph(ANALYTICS_EXCLUDED, secondary = true)
            buttonRow(
                secondaryButton("Privacy notice", Material2MZ.OPEN_IN_NEW) { linkOpener.open(privacyUrl) },
                secondaryButton("Contact", Material2MZ.MAIL_OUTLINE) { linkOpener.open(URI("mailto:$contactEmail")) },
            )
            versionCheck()
            feedbackSection(contactEmail)
        }

        /** The page for a build that cannot send usage statistics. */
        fun withoutAnalytics(contactEmail: String): PrivacyPage = PrivacyPage().apply {
            subheading(ANALYTICS_TITLE)
            paragraph("This version of the app does not send usage statistics. Your videos and projects stay on this computer.")
            versionCheck()
            feedbackSection(contactEmail)
        }

        /**
         * The requests to GitHub for the build expiry and the update (build-expiry-spec.md, "Privacy"). They are not
         * part of the usage statistics, so they have their own section in each build.
         */
        private fun PrivacyPage.versionCheck() {
            subheading(VERSION_CHECK_TITLE)
            VERSION_CHECK_TEXT.forEach { paragraph(it) }
        }

        const val VERSION_CHECK_TITLE = "Version check and updates"

        const val ANALYTICS_TITLE = "Usage statistics"
        const val ESSENTIAL_SWITCH = "Send essential statistics"
        const val EXTENDED_SWITCH = "Send extended statistics"

        /** The short version of the analytics notice (site/public/privacy/index.html, "Usage statistics"). Keep the two in agreement. */
        const val ANALYTICS_ESSENTIAL =
            "Essential statistics are on by default: app version, OS family, session length, the number of errors, " +
                "and a range for the number of sessions. They tell us how many people use the app. " +
                "Turn them off to send no statistics at all."
        const val ANALYTICS_EXTENDED =
            "Extended statistics also send counts of the tabs and features you use, the export results, " +
                "the theme and language settings, and the sports of your projects. They need the essential statistics."
        const val ANALYTICS_EXCLUDED =
            "Excluded: video, audio, file names, paths, project names, scores, player names, text that you type, " +
                "user or device IDs, and error details."

        val VERSION_CHECK_TEXT = listOf(
            "${AppInfo.NAME} reads a small file from GitHub when it starts and from time to time while it runs. " +
                "The file tells if this version still works and if a new version is available.",
            "The request sends no user ID and no usage statistics. GitHub receives your IP address and the standard " +
                "data of a web request.",
            "\"Update and restart\" downloads the setup file of the new version from GitHub. This request also sends " +
                "no user ID.",
            "The version check is necessary for the license, so you cannot turn it off. It is not part of the usage statistics.",
        )
    }
}
