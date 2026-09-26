package org.litvin.ui.privacy

import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsPreferences
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.SectionPage
import java.net.URI
import javax.swing.JCheckBox

/** The Privacy section of the More window. */
class PrivacyPage private constructor() : SectionPage(TITLE) {
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
            val toggle = JCheckBox("Send optional usage analytics", preferences.resolve().isEnabled).apply {
                name = "more-privacy-analytics"
                UiStyles.styleCheckBox(this)
                addActionListener { if (isSelected) controller.enable() else controller.disable() }
            }
            addItem(toggle, gapAfter = 8)
            paragraph("Collected: approved product action categories only.")
            paragraph(
                "Excluded: video, audio, filenames, paths, project data, scores, player data, identifiers, and diagnostics.",
                secondary = true,
            )
            buttonRow(
                secondaryButton("Privacy notice") { linkOpener.open(privacyUrl) },
                secondaryButton("Contact") { linkOpener.open(contact) },
            )
        }

        /** The page for a build that cannot send usage analytics. */
        fun withoutAnalytics(): PrivacyPage = PrivacyPage().apply {
            subheading("Usage analytics")
            paragraph("This version of the app does not send usage analytics. Your videos and projects stay on this computer.")
        }
    }
}
