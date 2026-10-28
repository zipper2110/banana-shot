package org.litvin.ui.privacy

import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsLevel
import org.litvin.analytics.AnalyticsPreferences.Choice
import org.litvin.analytics.AnalyticsPreferences
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.SectionPage
import org.litvin.ui.commons.SwitchBox
import java.awt.Component
import java.net.URI
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.tr

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
        subheading(tr("privacy.feedback.title"))
        paragraph(tr("privacy.feedback.when")).name = "more-privacy-feedback"
        paragraph(tr("privacy.feedback.contents", "attach" to tr("feedback.attachLog")))
        paragraph(tr("privacy.feedback.server"), secondary = true)
        paragraph(tr("privacy.feedback.delete", "email" to contactEmail), secondary = true)
    }

    companion object {
        val TITLE: String get() = tr("privacy.title")

        /**
         * The privacy notice on the site is in English only, and the English text is binding (B-11 plan,
         * "Decisions", "Legal texts"). A translated app tells this next to each link to the notice. English has no note.
         */
        internal val NOTICE_IN_ENGLISH: String? get() =
            if (I18n.language == AppLanguage.SOURCE) null else tr("privacy.notice.english")

        /**
         * The page for a build that sends usage statistics. One switch turns all statistics on and off. The other
         * switch selects the extended statistics. It works only while the essential statistics are on.
         * [askToKeep] runs when the user turns the essential statistics off. It returns true when the user keeps them on.
         */
        fun withAnalytics(
            controller: AnalyticsController,
            preferences: AnalyticsPreferences,
            privacyUrl: URI,
            contactEmail: String,
            askToKeep: (Component) -> Boolean = { false },
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
                if (!essential.isSelected && askToKeep(essential)) {
                    essential.isSelected = true
                    return@addActionListener
                }
                extended.isEnabled = essential.isSelected
                if (!essential.isSelected) extended.isSelected = false
                controller.choose(if (essential.isSelected) Choice.ESSENTIAL else Choice.OFF)
            }
            addItem(essential)
            paragraph(ANALYTICS_ESSENTIAL)
            addItem(extended)
            paragraph(ANALYTICS_EXTENDED)
            paragraph(ANALYTICS_EXCLUDED, secondary = true)
            NOTICE_IN_ENGLISH?.let { paragraph(it, secondary = true).name = "more-privacy-notice-english" }
            buttonRow(
                secondaryButton(tr("privacy.notice"), Material2MZ.OPEN_IN_NEW) { linkOpener.open(privacyUrl) },
                secondaryButton(tr("privacy.contact"), Material2MZ.MAIL_OUTLINE) { linkOpener.open(URI("mailto:$contactEmail")) },
            )
            versionCheck()
            feedbackSection(contactEmail)
        }

        /** The page for a build that cannot send usage statistics. */
        fun withoutAnalytics(contactEmail: String): PrivacyPage = PrivacyPage().apply {
            subheading(ANALYTICS_TITLE)
            paragraph(tr("privacy.noAnalytics"))
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

        val VERSION_CHECK_TITLE: String get() = tr("privacy.versionCheck.title")

        val ANALYTICS_TITLE: String get() = tr("privacy.analytics.title")
        val ESSENTIAL_SWITCH: String get() = tr("privacy.essential.switch")
        val EXTENDED_SWITCH: String get() = tr("privacy.extended.switch")

        /** The short version of the analytics notice (site/public/privacy/index.html, "Usage statistics"). Keep the two in agreement. */
        val ANALYTICS_ESSENTIAL: String get() = tr("privacy.essential")
        val ANALYTICS_EXTENDED: String get() = tr("privacy.extended")
        val ANALYTICS_EXCLUDED: String get() = tr("privacy.excluded")

        val VERSION_CHECK_TEXT: List<String>
            get() = listOf(
                tr("privacy.versionCheck.reads", "app" to AppInfo.NAME),
                tr("privacy.versionCheck.request"),
                tr("privacy.versionCheck.update", "button" to tr("expiry.updateAndRestart")),
                tr("privacy.versionCheck.required"),
            )
    }
}
