package org.litvin.ui.privacy

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

    companion object {
        const val TITLE = "Privacy"

        /** The page for a build that can send usage analytics. The user can turn the analytics on and off. */
        fun withAnalytics(
            controller: AnalyticsController,
            preferences: AnalyticsPreferences,
            privacyUrl: URI,
            contact: URI,
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
                secondaryButton("Contact", Material2MZ.MAIL_OUTLINE) { linkOpener.open(contact) },
            )
        }

        /** The page for a build that cannot send usage analytics. */
        fun withoutAnalytics(): PrivacyPage = PrivacyPage().apply {
            subheading("Usage analytics")
            paragraph("This version of the app does not send usage analytics. Your videos and projects stay on this computer.")
        }
    }
}
