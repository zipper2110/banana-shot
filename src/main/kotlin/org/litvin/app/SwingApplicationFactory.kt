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
import org.litvin.ui.commons.HintController
import org.litvin.ui.commons.HintId
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.VideoErrorPanel
import org.litvin.ui.expiry.ExpiryUi
import org.litvin.ui.expiry.UpdateRunner
import org.litvin.license.update.UpdateAndRestart
import org.litvin.feedback.FeedbackLog
import org.litvin.feedback.FeedbackPreferences
import org.litvin.feedback.FeedbackSystemInfo
import org.litvin.feedback.FeedbackTopic
import org.litvin.ui.feedback.FeedbackLauncher
import org.litvin.ui.feedback.presenter.DefaultFeedbackPresenter
import org.litvin.ui.feedback.presenter.FeedbackRequest
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
import org.litvin.ui.commons.PreferencesHintRegistry
import org.litvin.ui.commons.PreferencesCommentStyleDefaults
import org.litvin.ui.tabs.scoring.PreferencesScoreboardStyleDefaults
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import org.litvin.ui.tabs.stats.SwingStatsPanel
import org.litvin.ui.tabs.test.SwingTestPanel
import org.litvin.RenderQueueManager
import org.litvin.analytics.Analytics
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsEvent
import org.litvin.analytics.DisabledAnalytics
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
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
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
    private const val HELP_BUTTON_HINT = "Help is always here. Each tab has its own help page. Press F1 on any tab."
    private const val GO_TO_SCORING_HINT = "When you finish marking, go to Scoring and give each point a winner."

    /** The Scoring hint shows when the project has this number of marked points. */
    private const val GO_TO_SCORING_POINTS = 4

    /**
     * The reply address of the feedback (decision 7 of B-8). When the app domain exists (B-10), change it here to an
     * address on that domain. The Contact page, the Privacy page, and the feedback form show it.
     */
    private const val CONTACT_EMAIL = "leetvin@gmail.com"

    /** The feedback form. The send and the read of the log run on their own executor. */
    private fun feedbackPresenter(services: AppServices) = DefaultFeedbackPresenter(
        sender = services.feedbackSender,
        readLog = FeedbackLog(services.paths.logs)::read,
        preferences = FeedbackPreferences(services.preferences.node(PreferencesProvider.APPLICATION)),
        system = FeedbackSystemInfo.current(AppInfo.version),
        contactEmail = CONTACT_EMAIL,
        copyToClipboard = { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) },
        openMail = { uri -> runCatching { Desktop.getDesktop().mail(uri) }.isSuccess },
        background = services.executors.createExecutor("feedback-send"),
        ui = EventQueue::invokeLater,
    )

    /**
     * With one GPU, the GPU preference has no effect, so the restart popup is not necessary (B-35).
     * If the GPU count is not available ([gpuCount] is null), the popup shows. A missing popup on a
     * system with two GPUs keeps the app on the slow GPU until the next start. An unnecessary popup
     * on a system with one GPU shows only one time.
     */
    internal fun shouldShowGpuRestartNotification(
        show: Boolean,
        testEnabled: Boolean,
        gpuPreferenceChanged: Boolean,
        gpuCount: Int?,
    ): Boolean = show && !testEnabled && gpuPreferenceChanged && (gpuCount == null || gpuCount > 1)

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
        updateAndRestart: UpdateAndRestart = UpdateAndRestart(),
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
        val analyticsController = services.analyticsController
        val analytics: Analytics = analyticsController ?: DisabledAnalytics

        try {
            val feedback = FeedbackLauncher(frame, feedbackPresenter(services))
            closeActions += feedback::close
            // "Report this problem" of an error dialog: the topic Problem, the error text, and the log files (T3 of B-8).
            fun reportProblem(title: String, message: String) =
                feedback.open(FeedbackRequest(FeedbackTopic.PROBLEM, "$title\n\n$message", attachLog = true))
            // The video error views are made by the media players of AppServices, before this window exists.
            val reportVideoProblem: (String, String) -> Unit = ::reportProblem
            VideoErrorPanel.onReportProblem = reportVideoProblem
            closeActions += { if (VideoErrorPanel.onReportProblem === reportVideoProblem) VideoErrorPanel.onReportProblem = null }
            val helpDialog = lazy { HelpDialog(frame, onTellUs = { feedback.open(FeedbackRequest(FeedbackTopic.QUESTION)) }) }
            fun showHelp(page: HelpPage, tab: HelpPage? = page) = helpDialog.value.open(page, tab)

            // Exports report their facts to the analytics (B-9). The exit stops this before the exports stop.
            val renderAnalytics = RenderAnalytics(analytics)
            RenderQueueManager.runListener = renderAnalytics
            closeActions += { if (RenderQueueManager.runListener === renderAnalytics) RenderQueueManager.runListener = null }

            val sidebar = JPanel().apply {
                UiStyles.styleSidebarContainer(this)
                preferredSize = Dimension(81, 0)
                foreground = Palette.FG
            }

            lateinit var btnProjects: UiStyles.SidebarButton
            lateinit var btnPoints: UiStyles.SidebarButton
            lateinit var btnColors: UiStyles.SidebarButton
            lateinit var btnScoring: UiStyles.SidebarButton
            lateinit var btnStats: UiStyles.SidebarButton
            lateinit var btnExport: UiStyles.SidebarButton
            lateinit var btnCropRotate: UiStyles.SidebarButton
            var btnTest: UiStyles.SidebarButton? = null

            // True while the app clicks a sidebar button. The analytics count only the clicks of the user (B-9).
            var navigatingInCode = false
            fun clickInCode(button: UiStyles.SidebarButton) {
                navigatingInCode = true
                try {
                    button.doClick()
                } finally {
                    navigatingInCode = false
                }
            }

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
            val hints = HintController(PreferencesHintRegistry(applicationPreferences))
            val commentStyles = PreferencesCommentStyleDefaults(applicationPreferences)

            val pointsPanel = SwingPointsPanel(
                services.mediaPlayers.create(MediaScreen.POINTS),
                services.adjustments,
                services.executors.createExecutor("points-autosave"),
                services.dialogs,
                hints,
                analytics,
                commentStyles,
            )
            closeActions += pointsPanel::close

            val colorsPanel = SwingColorAdjustmentsPanel(
                services.mediaPlayers.create(MediaScreen.COLORS),
                services.adjustments,
                services.preferences.node(PreferencesProvider.COLOR_ADJUSTMENTS),
                analytics,
            )
            closeActions += colorsPanel::close

            val cropRotatePanel = SwingCropRotatePanel(
                services.mediaPlayers.create(MediaScreen.CROP),
                DefaultCropRotatePresenter(services.adjustments, analytics),
            )
            closeActions += cropRotatePanel::dispose

            val scoringPreferences = services.preferences.node(PreferencesProvider.SCORING)
            val scoringPanel = SwingScoringPanel(
                services.mediaPlayers.create(MediaScreen.SCORING),
                services.adjustments,
                services.dialogs,
                PreferencesScoreboardStyleDefaults(scoringPreferences),
                hints = hints,
                analytics = analytics,
                commentStyles = commentStyles,
            )
            closeActions += scoringPanel::close
            scoringPanel.onGoToPoint = { pointId ->
                clickInCode(btnPoints)
                pointsPanel.selectPoint(pointId)
            }

            val statsPanel = SwingStatsPanel(
                services.dialogs,
                onOpenScoring = { clickInCode(btnScoring) },
                onOpenPoint = { pointId ->
                    clickInCode(btnScoring)
                    scoringPanel.selectPoint(pointId)
                },
            )

            val exportPanel = SwingExportPanel(
                ExportSettingsPreferences(services.preferences.node(PreferencesProvider.EXPORT)),
                services.renderService,
                services.adjustments,
                services.completedRenders,
                services.filePicker,
                services.dialogs,
                services.encoderCapabilities,
                hints,
            )
            closeActions += exportPanel::close

            val testEnabled = System.getProperty("test") == "true"
            val testPanel = if (testEnabled) SwingTestPanel() else null
            if (testPanel != null) closeActions += testPanel::onDeactivated
            lateinit var projectsPanel: SwingProjectsPanel

            var tabTitle = "Projects"
            var projectName: String? = null
            var projectOpen = false
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

            fun analyticsTab(card: String): AnalyticsEvent.Tab? = when (card) {
                CARD_PROJECTS -> AnalyticsEvent.Tab.PROJECTS
                CARD_POINTS -> AnalyticsEvent.Tab.POINTS
                CARD_ADJ_COLORS -> AnalyticsEvent.Tab.COLORS
                CARD_ADJ_CROP_ROTATE -> AnalyticsEvent.Tab.CROP_ROTATE
                CARD_SCORING -> AnalyticsEvent.Tab.SCORING
                CARD_STATS -> AnalyticsEvent.Tab.STATS
                CARD_EXPORT -> AnalyticsEvent.Tab.EXPORT
                else -> null
            }

            /** Shows [card]. [byUser] is true only for a click of the user on a sidebar button. */
            fun goTo(card: String, byUser: Boolean = false) {
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
                // The Scoring hint points at the Scoring button while the Points tab is open. Scoring is the action that it teaches.
                if (card == CARD_SCORING) hints.dismiss(HintId.GO_TO_SCORING) else hints.hide(HintId.GO_TO_SCORING)
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
                analytics.record(AnalyticsEvent.TabShown(analyticsTab(card), byUser))
            }

            val projectsPresenter = DefaultProjectsPresenter(
                services.projectsRepository,
                services.preferences.node(PreferencesProvider.PROJECTS),
                services.executors.createExecutor("projects-io"),
                analytics,
            )
            projectsPanel = SwingProjectsPanel(
                projectsPresenter,
                services.filePicker,
                services.dialogs,
            ).apply {
                onProjectOpened = { path ->
                    projectOpen = true
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
                goTo(CARD_PROJECTS, byUser = !navigatingInCode)
            }.apply { name = "nav-projects" }
            addItem(btnProjects)
            addGroup(groupMatch)
            btnPoints = UiStyles.sidebarButton("Points", UiStyles.pointsIcon()) {
                showTitle("Points")
                goTo(CARD_POINTS, byUser = !navigatingInCode)
            }.apply { name = "nav-points" }
            addItem(btnPoints, groupMatch)
            btnScoring = UiStyles.sidebarButton("Scoring", UiStyles.targetIcon()) {
                showTitle("Scoring")
                goTo(CARD_SCORING, byUser = !navigatingInCode)
            }.apply { name = "nav-scoring" }
            addItem(btnScoring, groupMatch)
            pointsPanel.onMarkedPointCount = { count ->
                if (count >= GO_TO_SCORING_POINTS) hints.show(HintId.GO_TO_SCORING, btnScoring, GO_TO_SCORING_HINT)
            }
            btnStats = UiStyles.sidebarButton("Stats", UiStyles.statsIcon()) {
                showTitle("Statistics")
                goTo(CARD_STATS, byUser = !navigatingInCode)
            }.apply { name = "nav-stats" }
            addItem(btnStats, groupMatch)
            addGroup(groupVideo)
            btnColors = UiStyles.sidebarButton("Colors", UiStyles.colorsIcon()) {
                showTitle("Color")
                goTo(CARD_ADJ_COLORS, byUser = !navigatingInCode)
            }.apply { name = "nav-colors" }
            addItem(btnColors, groupVideo)
            btnCropRotate = UiStyles.sidebarButton("Transform", UiStyles.cropRotateIcon()) {
                showTitle("Transform")
                goTo(CARD_ADJ_CROP_ROTATE, byUser = !navigatingInCode)
            }.apply { name = "nav-crop" }
            addItem(btnCropRotate, groupVideo)
            // addItem adds the usual button gap before Export. Together they make the same gap as between the groups.
            sidebar.add(Box.createRigidArea(Dimension(0, SIDEBAR_GROUP_GAP - SIDEBAR_BUTTON_GAP)))
            btnExport = UiStyles.sidebarButton("Export", UiStyles.exportIcon()) {
                showTitle("Export")
                goTo(CARD_EXPORT, byUser = !navigatingInCode)
            }.apply { name = "nav-export" }
            addItem(btnExport)
            // The export queue does not depend on the project, so the Export button always shows (B-32).
            // The badge shows the number of running and queued exports.
            fun showExportCount(count: Int) {
                btnExport.badgeCount = count
                btnExport.accessibleContext.accessibleDescription =
                    if (count > 0) "$count running or queued export(s)" else null
            }
            exportPanel.onActiveExportCountChanged = ::showExportCount
            showExportCount(exportPanel.activeExportCount)
            val analyticsConfig = services.analyticsConfig as? AnalyticsBuildConfig.Enabled
            if (testEnabled) {
                btnTest = UiStyles.sidebarButton("Test", UiStyles.targetIcon()) {
                    showTitle("Test")
                    goTo(CARD_TEST, byUser = !navigatingInCode)
                }
                addItem(btnTest!!)
            }
            sidebar.add(Box.createVerticalGlue())
            fun openContextHelp() {
                analytics.record(AnalyticsEvent.HelpOpened)
                hints.dismiss(HintId.HELP_BUTTON)
                showHelp(currentHelpPage())
            }
            val btnHelp = UiStyles.sidebarButton("Help", UiStyles.helpIcon()) {
                openContextHelp()
            }.apply {
                name = "nav-help"
                toolTipText = "F1 - Help"
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            }
            // The Feedback button is always visible, also with no open project (T3 of B-8).
            sidebar.add(UiStyles.sidebarButton("Feedback", UiStyles.feedbackIcon()) {
                feedback.open()
            }.apply {
                name = "nav-feedback"
                toolTipText = "Send a problem, an idea, or a question to the author"
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            })
            sidebar.add(Box.createRigidArea(Dimension(0, 6)))
            sidebar.add(btnHelp)
            sidebar.add(Box.createRigidArea(Dimension(0, 6)))
            val moreDialog = lazy {
                val privacyPage = if (analyticsController != null && analyticsConfig != null && services.analyticsPreferences != null) {
                    PrivacyPage.withAnalytics(analyticsController, services.analyticsPreferences, analyticsConfig.privacyUrl, CONTACT_EMAIL)
                } else {
                    PrivacyPage.withoutAnalytics(CONTACT_EMAIL)
                }
                MoreDialog(
                    frame,
                    listOf(
                        MoreSection(
                            SettingsPage.TITLE,
                            SettingsPage(
                                services.paths.root,
                                onShowHintsAgain = {
                                    hints.resetAll()
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
                                onSendFeedback = { feedback.open() },
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
            projectGroups.forEach { it.isVisible = false }

            // Expired mode (E8-S1): only the Export tab shows. It uses the same mechanism that hides the tabs when no
            // project is open. A hidden card is not showing, so the keyboard shortcuts of its panel have no effect.
            fun setExpiredMode(expired: Boolean) {
                val projectTabs = !expired && projectOpen
                btnProjects.isVisible = !expired
                btnPoints.isVisible = projectTabs
                btnColors.isVisible = projectTabs
                btnCropRotate.isVisible = projectTabs
                btnScoring.isVisible = projectTabs
                btnStats.isVisible = projectTabs
                btnTest?.isVisible = projectTabs
                projectGroups.forEach { it.isVisible = projectTabs }
                sidebar.revalidate()
                sidebar.repaint()
                exportPanel.newExportRefused = expired
                if (expired) {
                    showTitle("Export")
                    goTo(CARD_EXPORT)
                } else {
                    showTitle("Projects")
                    goTo(CARD_PROJECTS)
                }
            }

            // The exit sequence of the analytics ("Exit sequence" in docs/analytics/design.md): count the running
            // exports as interrupted, then start the last summary. The 500 ms limit starts here.
            fun beginAnalyticsExit() {
                if (RenderQueueManager.runListener === renderAnalytics) RenderQueueManager.runListener = null
                RenderQueueManager.runningExports().forEach(renderAnalytics::interrupted)
                analyticsController?.beginFinalSend()
            }

            val updateRunner = UpdateRunner(
                update = updateAndRestart,
                background = services.executors.createExecutor("update-download"),
                exportRuns = { exportPanel.activeExportCount > 0 },
                closeSequence = {
                    beginAnalyticsExit()
                    handle.close()
                },
                exit = onWindowClosed,
                openUrl = { url -> runCatching { Desktop.getDesktop().browse(URI(url)) } },
                parent = { frame },
            )
            val expiryUi = ExpiryUi(
                services.expiry,
                updateRunner,
                frame,
                // Leaving the open tab saves the project and stops the player.
                beforeExpiredMode = { goTo(CARD_EXPORT) },
                setExpiredMode = ::setExpiredMode,
                windowShows = { frame.isShowing },
            )
            closeActions += expiryUi::close
            exportPanel.onNewExportRefused = services.expiry::showExpiredDialog
            exportPanel.onReportProblem = ::reportProblem
            handle.reportProblem = ::reportProblem

            frame.add(sidebar, BorderLayout.WEST)
            frame.add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(expiryUi.bar, BorderLayout.NORTH)
                add(cards, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            frame.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(AppShortcuts.HELP.keyStroke), "openContextHelp")
            frame.rootPane.actionMap.put("openContextHelp", object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    openContextHelp()
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
                    beginAnalyticsExit()
                    // The user does not see the wait for the last summary.
                    frame.isVisible = false
                    try {
                        handle.close()
                    } finally {
                        onWindowClosed()
                    }
                }
            })

            // Active time (B-9): the main window or one of its dialogs is the active window.
            val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            var appActive = false
            val activeWindowListener = PropertyChangeListener {
                val window = focusManager.activeWindow
                val active = window != null && generateSequence(window) { it.owner }.any { it === frame }
                if (active != appActive) {
                    appActive = active
                    analytics.record(AnalyticsEvent.WindowActive(active))
                }
            }
            focusManager.addPropertyChangeListener("activeWindow", activeWindowListener)
            closeActions += { focusManager.removePropertyChangeListener("activeWindow", activeWindowListener) }

            clickInCode(btnProjects)
            // After the Projects tab: in expired mode, the state hides it again and opens the Export tab.
            expiryUi.install()
            frame.isVisible = show

            // The first start shows the consent question, then the Overview help, then the hint at the Help button.
            fun showFirstLaunchOverview() {
                if (!HelpPreferences.claimFirstLaunchOverview(applicationPreferences)) return
                showHelp(HelpPage.OVERVIEW, currentHelpPage())
                helpDialog.value.addComponentListener(object : ComponentAdapter() {
                    override fun componentHidden(e: ComponentEvent) {
                        helpDialog.value.removeComponentListener(this)
                        hints.show(HintId.HELP_BUTTON, btnHelp, HELP_BUTTON_HINT)
                    }
                })
            }
            var askConsent = false
            if (show && analyticsController != null && analyticsConfig != null && services.analyticsPreferences?.resolve()?.needsChoice == true) {
                askConsent = true
                EventQueue.invokeLater {
                    AnalyticsConsentDialog.show(frame, analyticsController, analyticsConfig.privacyUrl, onClosed = { showFirstLaunchOverview() })
                }
            }

            if (shouldShowGpuRestartNotification(
                    show,
                    testEnabled,
                    WindowsGpuPreference.wasChangeApplied(),
                    WindowsGpuPreference.gpuCountAtChange(),
                )
            ) {
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
            if (show && !askConsent) showFirstLaunchOverview()

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
