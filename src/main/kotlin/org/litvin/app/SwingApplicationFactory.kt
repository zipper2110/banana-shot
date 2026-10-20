package org.litvin.app

import org.litvin.AppInfo
import org.litvin.ApplicationLayout
import org.litvin.SwingMainApp
import org.litvin.WindowsGpuPreference
import org.litvin.media.MediaScreen
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AppIcon
import org.litvin.ui.commons.AppTheme
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
import org.litvin.ui.tabs.adjustments.SwingAdjustVideoPanel
import org.litvin.ui.tabs.adjustments.presenter.AdjustSection
import org.litvin.ui.tabs.adjustments.presenter.DefaultAdjustVideoPresenter
import org.litvin.ui.tabs.export.ExportSettingsPreferences
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.points.PreferencesAppCreditRequest
import org.litvin.ui.tabs.points.PreferencesScoreboardStyleDefaults
import org.litvin.ui.tabs.points.SwingPointsPanel
import org.litvin.ui.tabs.projects.SwingProjectsPanel
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import org.litvin.ui.commons.PreferencesHintRegistry
import org.litvin.ui.commons.ThemeController
import org.litvin.ui.commons.ThemePreferences
import org.litvin.ui.commons.PreferencesCommentStyleDefaults
import org.litvin.ui.tabs.stats.SwingStatsPanel
import org.litvin.ui.tabs.share.SwingSharePanel
import org.litvin.share.PreferencesShareDefaults
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
import org.litvin.ui.more.LanguageSetting
import org.litvin.ui.more.MorePanel
import org.litvin.ui.commons.MessageButton
import org.litvin.ui.commons.UiButton
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.LanguageSettings
import org.litvin.i18n.Messages
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale
import org.litvin.ui.privacy.KeepStatisticsRequest
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
import java.util.concurrent.Executor
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.KeyStroke
import org.litvin.i18n.tr
import org.litvin.i18n.I18n

object SwingApplicationFactory {
    private val logger = KotlinLogging.logger {}
    private const val CARD_PROJECTS = "projects"
    private const val CARD_POINTS = "points"
    private const val CARD_EXPORT = "export"
    private const val CARD_STATS = "stats"
    private const val CARD_SHARE = "share"
    private const val CARD_ADJUST_VIDEO = "adjust-video"
    private const val CARD_TEST = "test"
    private const val SIDEBAR_BUTTON_GAP = 6
    private const val SIDEBAR_GROUP_GAP = 28
    private val HELP_BUTTON_HINT get() = tr("nav.help.hint")

    /**
     * The reply address of the feedback (decision 7 of B-8). When the app domain exists (B-10), change it here to an
     * address on that domain. The Contact page, the Privacy page, and the feedback form show it.
     */
    private const val CONTACT_EMAIL = "contact@banana-shot-editor.app"

    /** The feedback form. The send and the read of the log run on their own executor. */
    private fun feedbackPresenter(services: AppServices) = DefaultFeedbackPresenter(
        sender = services.feedbackSender,
        readLog = FeedbackLog(services.paths.logs)::read,
        preferences = FeedbackPreferences(services.preferences.node(PreferencesProvider.APPLICATION)),
        system = FeedbackSystemInfo.current(
            AppInfo.version,
            appLanguage = AnalyticsEvent.Language.ofCode(I18n.language.baseCode).key,
            osLanguage = osLanguage().key,
        ),
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
            author = AppInfo.AUTHOR,
            website = URI(AppInfo.WEBSITE),
            documents = listOf(
                document(tr("about.document.license"), "legal/LICENSE.txt", "LICENSE"),
                document(tr("about.document.licenseNotice"), "legal/LICENSE-NOTICE.txt", "LICENSE-NOTICE"),
                document(tr("about.document.thirdParty"), "legal/THIRD-PARTY-NOTICES.txt", "distribution/THIRD-PARTY-NOTICES.txt"),
            ),
            components = listOf(
                "Java" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
                "libmpv" to (layout.mpvDirectory?.path ?: tr("about.notFound")),
                "FFmpeg" to layout.ffmpegExecutable,
            ),
        )
    }

    /** The language row of More > Settings. [restart] restarts the app with the launcher (B-11, E3-T21). */
    private fun languageSetting(settings: LanguageSettings, restartLauncher: File?, restart: (File) -> Unit): LanguageSetting {
        val resolution = SwingMainApp.languageResolution ?: LanguageSettings.Resolution(I18n.language, LanguageSettings.Source.SYSTEM)
        return object : LanguageSetting {
            override val choice: AppLanguage? get() = settings.choice
            override val running: AppLanguage = resolution.language
            override val systemLanguage: AppLanguage = LanguageSettings.systemLanguage(Locale.getDefault(Locale.Category.DISPLAY))
            override fun save(choice: AppLanguage?): Boolean = runCatching { settings.save(choice) }
                .onFailure { logger.warn(it) { "Cannot save the language setting." } }
                .isSuccess
            override val restart: (() -> Unit)? = restartLauncher?.let { launcher -> { restart(launcher) } }
        }
    }

    @Volatile
    private var restarting = false

    /**
     * Restarts the app after a language change. It uses the close sequence of "Update and restart": the user confirms
     * the stop of a running export, the app saves the project and the export queue, then the new process starts and
     * this process exits. If the close fails, the app does not start again: the user sees the error, and the new
     * language applies at the next normal start.
     */
    private fun restartApp(frame: JFrame, launcher: File, exportRuns: () -> Boolean, exit: () -> Unit, closeSequence: () -> Unit) {
        if (restarting) return
        if (exportRuns() && !MessageDialog.confirm(frame, tr("restart.exportRuns.title"), tr("restart.exportRuns.text"), tr("settings.language.restart"))) {
            return
        }
        restarting = true
        frame.isVisible = false
        val closed = try {
            closeSequence()
            true
        } catch (failure: Exception) {
            logger.error(failure) { "The close sequence before the restart failed. The app does not start again." }
            MessageDialog.show(null, MessageKind.ERROR, tr("restart.failed.title"), tr("restart.failed.text"))
            false
        }
        if (closed) {
            try {
                AppRestart.start(launcher)
                logger.info { "Started $launcher for the restart. The app exits." }
            } catch (failure: Exception) {
                logger.error(failure) { "The restart with $launcher failed." }
            }
        }
        exit()
    }

    /**
     * The notice after an automatic language change (E4-T11): the app follows Windows, and Windows has a language that
     * the app now has. It shows one time. A new user does not see it. "Use English" is in English, so that a user who
     * cannot read the new language can use it.
     */
    private fun showLanguageNotice(frame: JFrame, settings: LanguageSettings, firstStart: Boolean, setting: LanguageSetting) {
        val resolution = SwingMainApp.languageResolution ?: return
        if (!settings.needsSystemLanguageNotice(resolution)) return
        settings.noticeShown(resolution.language)
        if (firstStart) return
        EventQueue.invokeLater {
            val useEnglish = Messages.english().text("language.notice.useEnglish")
            val clicked = MessageDialog.show(
                frame,
                MessageKind.INFO,
                tr("language.notice.title"),
                tr("language.notice.text", "language" to resolution.language.nativeName),
                listOf(MessageButton(useEnglish), MessageButton(tr("common.ok"), UiButton.Kind.LIME)),
                defaultIndex = 1,
                cancelIndex = 1,
            )
            if (clicked == 0 && setting.save(AppLanguage.ENGLISH)) {
                val restart = setting.restart
                if (restart != null) restart() else {
                    MessageDialog.show(frame, MessageKind.INFO, useEnglish, Messages.english().text("settings.language.closeAndStart"))
                }
            }
        }
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
                val labels = listOf("nav.projects", "nav.points", "nav.stats", "nav.adjust", "nav.thumbnail", "nav.export", "nav.help", "nav.feedback", "nav.more")
                preferredSize = Dimension(UiStyles.sidebarWidth(labels.map { tr(it) }), 0)
                foreground = Palette.FG
            }

            lateinit var btnProjects: UiStyles.SidebarButton
            lateinit var btnPoints: UiStyles.SidebarButton
            lateinit var btnAdjustVideo: UiStyles.SidebarButton
            lateinit var btnStats: UiStyles.SidebarButton
            lateinit var btnExport: UiStyles.SidebarButton
            lateinit var btnShare: UiStyles.SidebarButton
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

            // Match tabs go in order. The Video tab has settings for the image that the user can change at any time.
            val groupMatch = UiStyles.SidebarGroup(tr("nav.group.match")).apply { name = "nav-group-match" }
            val groupVideo = UiStyles.SidebarGroup(tr("nav.group.video")).apply { name = "nav-group-video" }
            val projectGroups = listOf(groupMatch, groupVideo)

            fun addItem(button: UiStyles.SidebarButton, container: JPanel = sidebar) {
                button.alignmentX = 0f
                button.maximumSize = Dimension(Int.MAX_VALUE, maxOf(64, button.preferredSize.height))
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
            // Read before the first-launch overview claims it: a new user gets no notice of a language change (B-11).
            val firstStart = !HelpPreferences.wasOverviewShown(applicationPreferences)
            val languageSettings = LanguageSettings(applicationPreferences)
            lateinit var languageSetting: LanguageSetting
            // One controller for the theme controls of the Projects tab and More > Settings.
            val themeSettings = ThemeController(ThemePreferences(applicationPreferences))
            // The theme, the accent, and the language are session attributes of the analytics (B-44).
            fun recordSettings() = analytics.record(
                AnalyticsEvent.Settings(
                    theme = when (themeSettings.theme) {
                        AppTheme.DARK -> AnalyticsEvent.Theme.DARK
                        AppTheme.MID -> AnalyticsEvent.Theme.MID
                        AppTheme.LIGHT -> AnalyticsEvent.Theme.LIGHT
                    },
                    accent = if (themeSettings.customAccent == null) AnalyticsEvent.Accent.DEFAULT else AnalyticsEvent.Accent.CUSTOM,
                    // The primary subtag of the app language: "es" for both Spanish variants. The test language qps
                    // is English with changed texts, so it sends "en" (B-11 E2, E4-T10).
                    language = AnalyticsEvent.Language.ofCode(I18n.language.baseCode),
                ),
            )
            recordSettings()
            themeSettings.onChange(::recordSettings)
            val hints = HintController(PreferencesHintRegistry(applicationPreferences))
            val commentStyles = PreferencesCommentStyleDefaults(applicationPreferences)
            val scoreboardPreferences = services.preferences.node(PreferencesProvider.SCOREBOARD)

            val pointsPanel = SwingPointsPanel(
                services.mediaPlayers.create(MediaScreen.POINTS),
                services.adjustments,
                services.executors.createExecutor("points-autosave"),
                services.dialogs,
                hints,
                analytics,
                commentStyles,
                PreferencesScoreboardStyleDefaults(scoreboardPreferences),
                PreferencesAppCreditRequest(scoreboardPreferences),
            )
            closeActions += pointsPanel::close

            val adjustVideoPanel = SwingAdjustVideoPanel(
                services.mediaPlayers.create(MediaScreen.ADJUST_VIDEO),
                DefaultAdjustVideoPresenter(services.adjustments, analytics),
            )
            closeActions += adjustVideoPanel::close

            val statsPanel = SwingStatsPanel(
                services.dialogs,
                // The Points tab scores the points.
                onOpenPoints = { clickInCode(btnPoints) },
                onOpenPoint = { pointId ->
                    clickInCode(btnPoints)
                    pointsPanel.selectPoint(pointId)
                },
            )

            val exportSettings = ExportSettingsPreferences(services.preferences.node(PreferencesProvider.EXPORT))
            val exportPanel = SwingExportPanel(
                exportSettings,
                services.renderService,
                services.adjustments,
                services.completedRenders,
                services.filePicker,
                services.dialogs,
                services.encoderCapabilities,
                hints,
            )
            closeActions += exportPanel::close

            // The thumbnail goes to the folder of the last export (B-46).
            val sharePanel = SwingSharePanel(
                services.dialogs,
                PreferencesShareDefaults(services.preferences.node(PreferencesProvider.SHARE)),
                exportFolder = exportSettings::loadOutputDirectory,
                onOpenPoints = { clickInCode(btnPoints) },
            )
            closeActions += sharePanel::close

            val testEnabled = System.getProperty("test") == "true"
            val testPanel = if (testEnabled) SwingTestPanel() else null
            if (testPanel != null) closeActions += testPanel::onDeactivated
            lateinit var projectsPanel: SwingProjectsPanel

            var tabTitle = tr("tab.projects")
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
                // The help has a page for each sub-tab of Colors & Transform.
                CARD_ADJUST_VIDEO -> if (adjustVideoPanel.section == AdjustSection.TRANSFORM) HelpPage.CROP else HelpPage.COLORS
                CARD_STATS -> HelpPage.STATISTICS
                CARD_EXPORT -> HelpPage.EXPORT
                CARD_SHARE -> HelpPage.SHARE
                else -> HelpPage.OVERVIEW
            }

            fun analyticsTab(card: String): AnalyticsEvent.Tab? = when (card) {
                CARD_PROJECTS -> AnalyticsEvent.Tab.PROJECTS
                CARD_POINTS -> AnalyticsEvent.Tab.POINTS
                // Colors & Transform reports its open sub-tab with the keys of the older Colors and Transform tabs.
                CARD_ADJUST_VIDEO -> DefaultAdjustVideoPresenter.analyticsTab(adjustVideoPanel.section)
                CARD_STATS -> AnalyticsEvent.Tab.STATS
                CARD_EXPORT -> AnalyticsEvent.Tab.EXPORT
                CARD_SHARE -> AnalyticsEvent.Tab.THUMBNAIL
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
                    CARD_ADJUST_VIDEO -> adjustVideoPanel.onDeactivated()
                    CARD_SHARE -> sharePanel.onDeactivated()
                    CARD_TEST -> testPanel?.onDeactivated()
                }
                cardLayout.show(cards, card)
                when (card) {
                    CARD_PROJECTS -> projectsPanel.onActivated()
                    CARD_POINTS -> pointsPanel.onActivated()
                    CARD_ADJUST_VIDEO -> adjustVideoPanel.onActivated()
                    CARD_STATS -> statsPanel.onActivated()
                    CARD_EXPORT -> exportPanel.onActivated()
                    CARD_SHARE -> sharePanel.onActivated()
                    CARD_TEST -> testPanel?.onActivated()
                }
                btnProjects.active = card == CARD_PROJECTS
                btnPoints.active = card == CARD_POINTS
                btnAdjustVideo.active = card == CARD_ADJUST_VIDEO
                btnStats.active = card == CARD_STATS
                btnExport.active = card == CARD_EXPORT
                btnShare.active = card == CARD_SHARE
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
                themeSettings = themeSettings,
            ).apply {
                onProjectOpened = { path ->
                    projectOpen = true
                    pointsPanel.setProjectManifest(path)
                    adjustVideoPanel.setProjectManifest(path)
                    statsPanel.setProjectManifest(path)
                    exportPanel.setProjectManifest(path)
                    sharePanel.setProjectManifest(path)
                    testPanel?.setProjectManifest(path)
                    btnPoints.isVisible = true
                    btnAdjustVideo.isVisible = true
                    btnStats.isVisible = true
                    btnShare.isVisible = true
                    btnTest?.isVisible = true
                    projectGroups.forEach { it.isVisible = true }
                    sidebar.revalidate()
                    sidebar.repaint()
                    showTitle(tr("tab.points"))
                    goTo(CARD_POINTS)
                }
                onCurrentProjectNameChanged = { name ->
                    projectName = name
                    updateTitle()
                }
            }

            cards.add(projectsPanel, CARD_PROJECTS)
            cards.add(pointsPanel, CARD_POINTS)
            cards.add(adjustVideoPanel, CARD_ADJUST_VIDEO)
            cards.add(statsPanel, CARD_STATS)
            cards.add(exportPanel, CARD_EXPORT)
            cards.add(sharePanel, CARD_SHARE)
            if (testPanel != null) cards.add(testPanel, CARD_TEST)

            btnProjects = UiStyles.sidebarButton(tr("nav.projects"), UiStyles.folderIcon()) {
                showTitle(tr("tab.projects"))
                goTo(CARD_PROJECTS, byUser = !navigatingInCode)
            }.apply { name = "nav-projects" }
            addItem(btnProjects)
            addGroup(groupMatch)
            btnPoints = UiStyles.sidebarButton(tr("nav.points"), UiStyles.pointsIcon()) {
                showTitle(tr("tab.points"))
                goTo(CARD_POINTS, byUser = !navigatingInCode)
            }.apply { name = "nav-points" }
            addItem(btnPoints, groupMatch)
            btnStats = UiStyles.sidebarButton(tr("nav.stats"), UiStyles.statsIcon()) {
                showTitle(tr("tab.statistics"))
                goTo(CARD_STATS, byUser = !navigatingInCode)
            }.apply { name = "nav-stats" }
            addItem(btnStats, groupMatch)
            addGroup(groupVideo)
            btnAdjustVideo = UiStyles.sidebarButton(tr("nav.adjust"), UiStyles.cropRotateIcon()) {
                showTitle(tr("tab.adjust"))
                goTo(CARD_ADJUST_VIDEO, byUser = !navigatingInCode)
            }.apply { name = "nav-adjust-video" }
            addItem(btnAdjustVideo, groupVideo)
            // The Thumbnail tab makes a YouTube thumbnail for the open project, so it shows only with a project (B-46).
            btnShare = UiStyles.sidebarButton(tr("nav.thumbnail"), UiStyles.thumbnailIcon()) {
                showTitle(tr("tab.thumbnail"))
                goTo(CARD_SHARE, byUser = !navigatingInCode)
            }.apply { name = "nav-share" }
            addItem(btnShare, groupVideo)
            // addItem adds the usual button gap before Export. Together they make the same gap as between the groups.
            sidebar.add(Box.createRigidArea(Dimension(0, SIDEBAR_GROUP_GAP - SIDEBAR_BUTTON_GAP)))
            btnExport = UiStyles.sidebarButton(tr("nav.export"), UiStyles.exportIcon()) {
                showTitle(tr("tab.export"))
                goTo(CARD_EXPORT, byUser = !navigatingInCode)
            }.apply { name = "nav-export" }
            addItem(btnExport)
            // The export queue does not depend on the project, so the Export button always shows (B-32).
            // The badge shows the number of running and queued exports.
            fun showExportCount(count: Int) {
                btnExport.badgeCount = count
                btnExport.accessibleContext.accessibleDescription =
                    if (count > 0) tr("nav.export.count", "count" to count) else null
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
            val btnHelp = UiStyles.sidebarButton(tr("nav.help"), UiStyles.helpIcon()) {
                openContextHelp()
            }.apply {
                name = "nav-help"
                toolTipText = tr("nav.help.tooltip")
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            }
            // The Feedback button is always visible, also with no open project (T3 of B-8).
            sidebar.add(UiStyles.sidebarButton(tr("nav.feedback"), UiStyles.feedbackIcon()) {
                feedback.open()
            }.apply {
                name = "nav-feedback"
                toolTipText = tr("nav.feedback.tooltip")
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            })
            sidebar.add(Box.createRigidArea(Dimension(0, 6)))
            sidebar.add(btnHelp)
            sidebar.add(Box.createRigidArea(Dimension(0, 6)))
            val moreDialog = lazy {
                val privacyPage = if (analyticsController != null && analyticsConfig != null && services.analyticsPreferences != null) {
                    // Each time the user turns the statistics off, the app author asks to keep them on.
                    PrivacyPage.withAnalytics(
                        analyticsController, services.analyticsPreferences, analyticsConfig.privacyUrl, CONTACT_EMAIL,
                        askToKeep = KeepStatisticsRequest::ask,
                    )
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
                                themeSettings,
                                onShowHintsAgain = {
                                    hints.resetAll()
                                    HelpPreferences.resetFirstLaunchOverview(applicationPreferences)
                                },
                                onOpenFolder = ::openWithDesktop,
                                language = languageSetting,
                            ),
                            SettingsPage.ID,
                        ),
                        MoreSection(
                            AboutPage.TITLE,
                            AboutPage(aboutInfo(), onOpenFile = ::openWithDesktop, onOpenLink = PrivacyLinkOpener.DesktopBrowser::open),
                            AboutPage.ID,
                        ),
                        MoreSection(PrivacyPage.TITLE, privacyPage, MorePanel.PRIVACY_ID),
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
                            ContactPage.ID,
                        ),
                    ),
                )
            }
            sidebar.add(UiStyles.sidebarButton(tr("nav.more"), UiStyles.moreIcon()) {
                moreDialog.value.open()
            }.apply {
                name = "nav-more"
                toolTipText = tr("nav.more.tooltip")
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            })

            btnPoints.isVisible = false
            btnAdjustVideo.isVisible = false
            btnStats.isVisible = false
            btnShare.isVisible = false
            projectGroups.forEach { it.isVisible = false }

            // Expired mode (E8-S1): only the Export tab shows. It uses the same mechanism that hides the tabs when no
            // project is open. A hidden card is not showing, so the keyboard shortcuts of its panel have no effect.
            fun setExpiredMode(expired: Boolean) {
                val projectTabs = !expired && projectOpen
                btnProjects.isVisible = !expired
                btnPoints.isVisible = projectTabs
                btnAdjustVideo.isVisible = projectTabs
                btnStats.isVisible = projectTabs
                btnShare.isVisible = projectTabs
                btnTest?.isVisible = projectTabs
                projectGroups.forEach { it.isVisible = projectTabs }
                sidebar.revalidate()
                sidebar.repaint()
                exportPanel.newExportRefused = expired
                if (expired) {
                    showTitle(tr("tab.export"))
                    goTo(CARD_EXPORT)
                } else {
                    showTitle(tr("tab.projects"))
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

            languageSetting = languageSetting(languageSettings, restartLauncher = AppRestart.launcher()) { launcher ->
                restartApp(frame, launcher, exportRuns = { exportPanel.activeExportCount > 0 }, exit = onWindowClosed) {
                    beginAnalyticsExit()
                    handle.close()
                }
            }

            val updateRunner = UpdateRunner(
                update = updateAndRestart,
                // Not an executor of services.executors: the close sequence stops those executors, but this thread
                // must live until it starts the setup and exits the app.
                background = Executor { task ->
                    Thread(task, "bananashot-update-download").apply { isDaemon = true }.start()
                },
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
                    tr("gpu.title"),
                    tr("gpu.text"),
                    DialogKit.WIDE,
                )
            }
            if (show && !askConsent) showFirstLaunchOverview()
            if (show) showLanguageNotice(frame, languageSettings, firstStart, languageSetting)

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
