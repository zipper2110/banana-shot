package org.litvin.app

import org.litvin.AppInfo
import org.litvin.ApplicationLayout
import org.litvin.SwingMainApp
import org.litvin.WindowsGpuPreference
import org.litvin.media.MediaScreen
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AppIcon
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.help.HelpDialog
import org.litvin.ui.help.HelpPage
import org.litvin.ui.help.HelpPreferences
import org.litvin.ui.tabs.adjustments.SwingColorAdjustmentsPanel
import org.litvin.ui.tabs.crop.SwingCropRotatePanel
import org.litvin.ui.tabs.crop.presenter.DefaultCropRotatePresenter
import org.litvin.ui.tabs.export.ExportSettingsPreferences
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.points.SwingPointsPanel
import org.litvin.ui.tabs.projects.SwingProjectsPanel
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import org.litvin.ui.tabs.scoring.PreferencesScoreSettingsHint
import org.litvin.ui.tabs.scoring.PreferencesScoreboardStyleDefaults
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import org.litvin.ui.tabs.stats.SwingStatsPanel
import org.litvin.ui.tabs.test.SwingTestPanel
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsEvent
import org.litvin.ui.privacy.AnalyticsConsentDialog
import org.litvin.ui.more.AboutDocument
import org.litvin.ui.more.AboutInfo
import org.litvin.ui.more.AboutPage
import org.litvin.ui.more.ContactPage
import org.litvin.ui.more.MoreDialog
import org.litvin.ui.more.MoreSection
import org.litvin.ui.more.SettingsPage
import org.litvin.ui.privacy.PrivacyLinkOpener
import org.litvin.ui.privacy.PrivacyPage
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.net.URI
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.KeyStroke

object SwingApplicationFactory {
    private const val CARD_PROJECTS = "projects"
    private const val CARD_POINTS = "points"
    private const val CARD_EXPORT = "export"
    private const val CARD_SCORING = "scoring"
    private const val CARD_STATS = "stats"
    private const val CARD_ADJ_COLORS = "adjustments"
    private const val CARD_ADJ_CROP_ROTATE = "adjustments-crop-rotate"
    private const val CARD_TEST = "test"
    private const val SIDEBAR_BUTTON_GAP = 6
    private const val SIDEBAR_GROUP_GAP = 28
    private const val CONTACT_EMAIL = "leetvin@gmail.com"
    private val CONTACT = URI("mailto:$CONTACT_EMAIL")

    internal fun shouldShowGpuRestartNotification(
        show: Boolean,
        testEnabled: Boolean,
        gpuPreferenceChanged: Boolean,
    ): Boolean = show && !testEnabled && gpuPreferenceChanged

    private fun openWithDesktop(file: File): Boolean = runCatching { Desktop.getDesktop().open(file) }.isSuccess

    private fun aboutInfo(): AboutInfo {
        val layout = ApplicationLayout.current()
        fun document(title: String, vararg candidates: String) =
            AboutDocument(title, candidates.map { File(layout.appHome, it) }.firstOrNull { it.isFile } ?: File(layout.appHome, candidates.first()))
        return AboutInfo(
            appName = AppInfo.NAME,
            version = AppInfo.version,
            documents = listOf(
                document("License", "legal/LICENSE.txt", "LICENSE"),
                document("License notice", "legal/LICENSE-NOTICE.txt", "LICENSE-NOTICE"),
                document("Third-party notices", "legal/THIRD-PARTY-NOTICES.txt", "distribution/THIRD-PARTY-NOTICES.txt"),
            ),
            components = listOf(
                "Java" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
                "libmpv" to (layout.mpvDirectory?.path ?: "Not found"),
                "FFmpeg" to layout.ffmpegExecutable,
            ),
        )
    }

    fun create(
        services: AppServices,
        show: Boolean = true,
        onWindowClosed: () -> Unit = {},
    ): SwingApplicationHandle {
        check(EventQueue.isDispatchThread()) { "Swing application must be created on the EDT" }

        val frame = JFrame(AppInfo.displayName).apply {
            name = "app-frame"
            iconImages = AppIcon.windowImages()
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            layout = BorderLayout()
        }
        val closeActions = mutableListOf<() -> Unit>({ services.close() })
        val handle = SwingApplicationHandle(frame, closeActions)

        try {
            val helpDialog = lazy { HelpDialog(frame) }
            fun showHelp(page: HelpPage, tab: HelpPage? = page) = helpDialog.value.open(page, tab)

            val sidebar = JPanel().apply {
                UiStyles.styleSidebarContainer(this)
                preferredSize = Dimension(81, 0)
                foreground = UiStyles.SIDEBAR_FG
            }

            lateinit var btnProjects: UiStyles.SidebarButton
            lateinit var btnPoints: UiStyles.SidebarButton
            lateinit var btnColors: UiStyles.SidebarButton
            lateinit var btnScoring: UiStyles.SidebarButton
            lateinit var btnStats: UiStyles.SidebarButton
            lateinit var btnExport: UiStyles.SidebarButton
            lateinit var btnCropRotate: UiStyles.SidebarButton
            var btnTest: UiStyles.SidebarButton? = null

            // Match tabs go in order. Video tabs are settings for the image that the user can change at any time.
            val groupMatch = UiStyles.SidebarGroup("Match").apply { name = "nav-group-match" }
            val groupVideo = UiStyles.SidebarGroup("Video").apply { name = "nav-group-video" }
            val projectGroups = listOf(groupMatch, groupVideo)

            fun addItem(button: UiStyles.SidebarButton, container: JPanel = sidebar) {
                button.alignmentX = 0f
                button.maximumSize = Dimension(Int.MAX_VALUE, 64)
                if (container.componentCount > 0) container.add(Box.createRigidArea(Dimension(0, SIDEBAR_BUTTON_GAP)))
                container.add(button)
            }

            fun addGroup(group: JPanel) {
                sidebar.add(Box.createRigidArea(Dimension(0, SIDEBAR_GROUP_GAP)))
                sidebar.add(group)
            }

            val cards = JPanel(CardLayout())
            val cardLayout = cards.layout as CardLayout
            val applicationPreferences = services.preferences.node(PreferencesProvider.APPLICATION)

            val pointsPanel = SwingPointsPanel(
                services.mediaPlayers.create(MediaScreen.POINTS),
                services.adjustments,
                services.executors.createExecutor("points-autosave"),
                services.dialogs,
            )
            closeActions += pointsPanel::close

            val colorsPanel = SwingColorAdjustmentsPanel(
                services.mediaPlayers.create(MediaScreen.COLORS),
                services.adjustments,
                services.preferences.node(PreferencesProvider.COLOR_ADJUSTMENTS),
            )
            closeActions += colorsPanel::close

            val cropRotatePanel = SwingCropRotatePanel(
                services.mediaPlayers.create(MediaScreen.CROP),
                DefaultCropRotatePresenter(services.adjustments),
            )
            closeActions += cropRotatePanel::dispose

            val scoringPreferences = services.preferences.node(PreferencesProvider.SCORING)
            val scoreSettingsHint = PreferencesScoreSettingsHint(scoringPreferences)
            val scoringPanel = SwingScoringPanel(
                services.mediaPlayers.create(MediaScreen.SCORING),
                services.adjustments,
                services.dialogs,
                PreferencesScoreboardStyleDefaults(scoringPreferences),
                scoreSettingsHint = scoreSettingsHint,
            )
            closeActions += scoringPanel::close
            scoringPanel.onGoToPoint = { pointId ->
                btnPoints.doClick()
                pointsPanel.selectPoint(pointId)
            }

            val statsPanel = SwingStatsPanel(
                services.dialogs,
                onOpenScoring = { btnScoring.doClick() },
                onOpenPoint = { pointId ->
                    btnScoring.doClick()
                    scoringPanel.selectPoint(pointId)
                },
            )

            val exportPanel = SwingExportPanel(
                ExportSettingsPreferences(services.preferences.node(PreferencesProvider.EXPORT)),
                services.renderService,
                services.completedRenders,
                services.filePicker,
                services.dialogs,
                services.encoderCapabilities,
            )
            closeActions += exportPanel::close

            val testEnabled = System.getProperty("test") == "true"
            val testPanel = if (testEnabled) SwingTestPanel() else null
            if (testPanel != null) closeActions += testPanel::onDeactivated
            lateinit var projectsPanel: SwingProjectsPanel

            var tabTitle = "Projects"
            var projectName: String? = null
            fun updateTitle() {
                frame.title = listOfNotNull(AppInfo.NAME, tabTitle, projectName?.takeIf { it.isNotBlank() })
                    .joinToString(" — ")
            }
            fun showTitle(tab: String) {
                tabTitle = tab
                updateTitle()
            }

            var currentCard: String? = null
            fun currentHelpPage(): HelpPage = when (currentCard) {
                CARD_PROJECTS -> HelpPage.PROJECTS
                CARD_POINTS -> HelpPage.POINTS
                CARD_ADJ_COLORS -> HelpPage.COLORS
                CARD_ADJ_CROP_ROTATE -> HelpPage.CROP
                CARD_SCORING -> HelpPage.SCORING
                CARD_STATS -> HelpPage.STATISTICS
                CARD_EXPORT -> HelpPage.EXPORT
                else -> HelpPage.OVERVIEW
            }

            fun goTo(card: String) {
                if (card == currentCard) {
                    // A click on the open Projects tab reloads the list, for example to show a project
                    // that was added after the list loaded. Other tabs stay as they are, because a
                    // reactivation pauses the video and reloads the points.
                    if (card == CARD_PROJECTS) projectsPanel.onActivated()
                    return
                }
                when (currentCard) {
                    CARD_PROJECTS -> projectsPanel.onDeactivated()
                    CARD_POINTS -> pointsPanel.onDeactivated()
                    CARD_ADJ_COLORS -> colorsPanel.onDeactivated()
                    CARD_ADJ_CROP_ROTATE -> cropRotatePanel.onDeactivated()
                    CARD_SCORING -> scoringPanel.onDeactivated()
                    CARD_TEST -> testPanel?.onDeactivated()
                }
                cardLayout.show(cards, card)
                when (card) {
                    CARD_PROJECTS -> projectsPanel.onActivated()
                    CARD_POINTS -> pointsPanel.onActivated()
                    CARD_ADJ_COLORS -> colorsPanel.onActivated()
                    CARD_ADJ_CROP_ROTATE -> cropRotatePanel.onActivated()
                    CARD_SCORING -> scoringPanel.onActivated()
                    CARD_STATS -> statsPanel.onActivated()
                    CARD_EXPORT -> exportPanel.onActivated()
                    CARD_TEST -> testPanel?.onActivated()
                }
                btnProjects.active = card == CARD_PROJECTS
                btnPoints.active = card == CARD_POINTS
                btnColors.active = card == CARD_ADJ_COLORS
                btnCropRotate.active = card == CARD_ADJ_CROP_ROTATE
                btnScoring.active = card == CARD_SCORING
                btnStats.active = card == CARD_STATS
                btnExport.active = card == CARD_EXPORT
                btnTest?.active = card == CARD_TEST
                currentCard = card
            }

            val projectsPresenter = DefaultProjectsPresenter(
                services.projectsRepository,
                services.preferences.node(PreferencesProvider.PROJECTS),
                services.executors.createExecutor("projects-io"),
            )
            projectsPanel = SwingProjectsPanel(
                projectsPresenter,
                services.filePicker,
                services.dialogs,
            ).apply {
                onProjectOpened = { path ->
                    pointsPanel.setProjectManifest(path)
                    colorsPanel.setProjectManifest(path)
                    cropRotatePanel.setProjectManifest(path)
                    scoringPanel.setProjectManifest(path)
                    statsPanel.setProjectManifest(path)
                    exportPanel.setProjectManifest(path)
                    testPanel?.setProjectManifest(path)
                    btnPoints.isVisible = true
                    btnColors.isVisible = true
                    btnCropRotate.isVisible = true
                    btnScoring.isVisible = true
                    btnStats.isVisible = true
                    btnExport.isVisible = true
                    btnTest?.isVisible = true
                    projectGroups.forEach { it.isVisible = true }
                    sidebar.revalidate()
                    sidebar.repaint()
                    showTitle("Points")
                    goTo(CARD_POINTS)
                }
                onCurrentProjectNameChanged = { name ->
                    projectName = name
                    updateTitle()
                }
            }

            cards.add(projectsPanel, CARD_PROJECTS)
            cards.add(pointsPanel, CARD_POINTS)
            cards.add(colorsPanel, CARD_ADJ_COLORS)
            cards.add(cropRotatePanel, CARD_ADJ_CROP_ROTATE)
            cards.add(scoringPanel, CARD_SCORING)
            cards.add(statsPanel, CARD_STATS)
            cards.add(exportPanel, CARD_EXPORT)
            if (testPanel != null) cards.add(testPanel, CARD_TEST)

            btnProjects = UiStyles.sidebarButton("Projects", UiStyles.folderIcon()) {
                showTitle("Projects")
                goTo(CARD_PROJECTS)
            }.apply { name = "nav-projects" }
            addItem(btnProjects)
            addGroup(groupMatch)
            btnPoints = UiStyles.sidebarButton("Points", UiStyles.pointsIcon()) {
                showTitle("Points")
                goTo(CARD_POINTS)
            }.apply { name = "nav-points" }
            addItem(btnPoints, groupMatch)
            btnScoring = UiStyles.sidebarButton("Scoring", UiStyles.targetIcon()) {
                showTitle("Scoring")
                goTo(CARD_SCORING)
            }.apply { name = "nav-scoring" }
            addItem(btnScoring, groupMatch)
            btnStats = UiStyles.sidebarButton("Stats", UiStyles.statsIcon()) {
                showTitle("Statistics")
                goTo(CARD_STATS)
            }.apply { name = "nav-stats" }
            addItem(btnStats, groupMatch)
            addGroup(groupVideo)
            btnColors = UiStyles.sidebarButton("Colors", UiStyles.colorsIcon()) {
                showTitle("Color")
                goTo(CARD_ADJ_COLORS)
            }.apply { name = "nav-colors" }
            addItem(btnColors, groupVideo)
            btnCropRotate = UiStyles.sidebarButton("Transform", UiStyles.cropRotateIcon()) {
                showTitle("Transform")
                goTo(CARD_ADJ_CROP_ROTATE)
            }.apply { name = "nav-crop" }
            addItem(btnCropRotate, groupVideo)
            // addItem adds the usual button gap before Export. Together they make the same gap as between the groups.
            sidebar.add(Box.createRigidArea(Dimension(0, SIDEBAR_GROUP_GAP - SIDEBAR_BUTTON_GAP)))
            btnExport = UiStyles.sidebarButton("Export", UiStyles.exportIcon()) {
                showTitle("Export")
                goTo(CARD_EXPORT)
            }.apply { name = "nav-export" }
            addItem(btnExport)
            val analytics = services.analyticsController
            val analyticsConfig = services.analyticsConfig as? AnalyticsBuildConfig.Enabled
            if (testEnabled) {
                btnTest = UiStyles.sidebarButton("Test", UiStyles.targetIcon()) {
                    showTitle("Test")
                    goTo(CARD_TEST)
                }
                addItem(btnTest!!)
            }
            sidebar.add(Box.createVerticalGlue())
            sidebar.add(UiStyles.sidebarButton("Help", UiStyles.helpIcon()) {
                showHelp(currentHelpPage())
            }.apply {
                name = "nav-help"
                toolTipText = "F1 - Help"
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            })
            sidebar.add(Box.createRigidArea(Dimension(0, 6)))
            val moreDialog = lazy {
                val privacyPage = if (analytics != null && analyticsConfig != null && services.analyticsPreferences != null) {
                    PrivacyPage.withAnalytics(analytics, services.analyticsPreferences, analyticsConfig.privacyUrl, CONTACT)
                } else {
                    PrivacyPage.withoutAnalytics()
                }
                MoreDialog(
                    frame,
                    listOf(
                        MoreSection(
                            SettingsPage.TITLE,
                            SettingsPage(
                                services.paths.root,
                                onShowHintsAgain = {
                                    scoreSettingsHint.reset()
                                    HelpPreferences.resetFirstLaunchOverview(applicationPreferences)
                                },
                                onOpenFolder = ::openWithDesktop,
                            ),
                        ),
                        MoreSection(PrivacyPage.TITLE, privacyPage),
                        MoreSection(
                            AboutPage.TITLE,
                            AboutPage(aboutInfo(), onOpenFile = ::openWithDesktop),
                        ),
                        MoreSection(
                            ContactPage.TITLE,
                            ContactPage(
                                CONTACT_EMAIL,
                                AppInfo.version,
                                services.paths.logs,
                                onOpenLink = PrivacyLinkOpener.DesktopBrowser::open,
                                onOpenFolder = ::openWithDesktop,
                            ),
                        ),
                    ),
                )
            }
            sidebar.add(UiStyles.sidebarButton("More", UiStyles.moreIcon()) {
                moreDialog.value.open()
            }.apply {
                name = "nav-more"
                toolTipText = "Settings, privacy, and information about the app"
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            })

            btnPoints.isVisible = false
            btnColors.isVisible = false
            btnCropRotate.isVisible = false
            btnScoring.isVisible = false
            btnStats.isVisible = false
            btnExport.isVisible = false
            projectGroups.forEach { it.isVisible = false }

            frame.add(sidebar, BorderLayout.WEST)
            frame.add(cards, BorderLayout.CENTER)
            frame.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(AppShortcuts.HELP.keyStroke), "openContextHelp")
            frame.rootPane.actionMap.put("openContextHelp", object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    showHelp(currentHelpPage())
                }
            })

            val savedX = applicationPreferences.getInt("win.x", Int.MIN_VALUE)
            val savedY = applicationPreferences.getInt("win.y", Int.MIN_VALUE)
            val savedW = applicationPreferences.getInt("win.w", Int.MIN_VALUE)
            val savedH = applicationPreferences.getInt("win.h", Int.MIN_VALUE)
            val savedState = applicationPreferences.getInt("win.state", JFrame.NORMAL)
            if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE && savedW > 0 && savedH > 0) {
                frame.setBounds(savedX, savedY, savedW, savedH)
                frame.extendedState = savedState
            } else {
                frame.setSize(1200, 800)
                frame.setLocationRelativeTo(null)
            }

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent?) {
                    analytics?.record(AnalyticsEvent.SessionEnded)
                    try {
                        handle.close()
                    } finally {
                        onWindowClosed()
                    }
                }
            })

            btnProjects.doClick()
            frame.isVisible = show

            if (show && analytics != null && analyticsConfig != null && services.analyticsPreferences?.resolve()?.needsChoice == true) {
                EventQueue.invokeLater { AnalyticsConsentDialog.show(frame, analytics, analyticsConfig.privacyUrl) }
            }

            if (shouldShowGpuRestartNotification(show, testEnabled, WindowsGpuPreference.wasChangeApplied())) {
                MessageDialog.show(
                    frame,
                    MessageKind.INFO,
                    "GPU preference set",
                    "We set a Windows preference for this app to use the dedicated/external GPU on future launches.\n\n" +
                        "**Please restart the application now.**\n\n" +
                        "If it still uses the integrated GPU, open Windows Graphics Settings → Graphics performance preference, or NVIDIA/AMD control panel, and force the high‑performance GPU for javaw.exe (or your packaged EXE).",
                    DialogKit.WIDE,
                )
            }
            if (show && HelpPreferences.claimFirstLaunchOverview(applicationPreferences)) {
                showHelp(HelpPage.OVERVIEW, currentHelpPage())
            }

            return handle
        } catch (failure: Throwable) {
            try {
                handle.close()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }
}
