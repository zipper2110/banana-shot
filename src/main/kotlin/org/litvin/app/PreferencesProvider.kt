package org.litvin.app

import org.litvin.SwingMainApp
import org.litvin.license.BuildExpiry
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import java.util.prefs.Preferences

fun interface PreferencesProvider {
    fun node(key: String): Preferences

    companion object {
        const val APPLICATION = "application"
        const val PROJECTS = "projects"
        const val EXPORT = "export"
        /** The default scoreboard style and the app credit request of the Points tab. */
        const val SCOREBOARD = "scoreboard"
        const val ANALYTICS = "analytics"

        /** The Share values for the next project: the last thumbnail style, its colors, and the league (B-46). */
        const val SHARE = "share"

        /** The saved time and the expired flag of `build-expiry-spec.md`. */
        const val LICENSE = "license"

        fun production(): PreferencesProvider = ProductionPreferencesProvider
    }
}

private object ProductionPreferencesProvider : PreferencesProvider {
    private val packageClasses = mapOf(
        PreferencesProvider.APPLICATION to SwingMainApp::class.java,
        PreferencesProvider.PROJECTS to DefaultProjectsPresenter::class.java,
        PreferencesProvider.EXPORT to SwingExportPanel::class.java,
        PreferencesProvider.ANALYTICS to SwingMainApp::class.java,
        PreferencesProvider.LICENSE to BuildExpiry::class.java,
    )

    /**
     * Nodes with a fixed path. The scoreboard settings keep the node of the removed Scoring tab,
     * so that the saved settings of the users stay.
     */
    private val fixedPaths = mapOf(
        PreferencesProvider.SCOREBOARD to "/org/litvin/ui/tabs/scoring",
        PreferencesProvider.SHARE to "/org/litvin/ui/tabs/share",
    )

    override fun node(key: String): Preferences {
        fixedPaths[key]?.let { return Preferences.userRoot().node(it) }
        val packageClass = packageClasses[key] ?: throw IllegalArgumentException("Unknown preferences key: $key")
        return Preferences.userNodeForPackage(packageClass)
    }
}
