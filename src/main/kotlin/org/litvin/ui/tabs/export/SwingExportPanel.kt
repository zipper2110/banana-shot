package org.litvin.ui.tabs.export
import org.litvin.ActiveQueueSnapshot
import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.RenderService
import org.litvin.RenderStatus
import org.litvin.export.ExportChunkPlanner
import org.litvin.export.ExportFailureAdvice
import org.litvin.export.ExportPlanner
import org.litvin.export.ExportPointSummary
import org.litvin.export.ExportReadiness
import org.litvin.export.ExportSourceInfo
import org.litvin.export.ExportSourceProbe
import org.litvin.export.ExportRenderPlanRequest
import org.litvin.export.RenderFormatting
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import org.litvin.stats.StatsIO
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.HintBalloon
import org.litvin.ui.commons.HintController
import org.litvin.ui.commons.HintId
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.applyDarkScrollbar

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.*
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.border.EmptyBorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 4 — Export pipeline (Swing)
 *
 * The left column has three numbered steps: the content of the video, the parts to include, and the quality.
 * Its footer shows the estimated file size and the start button.
 * The right side is one table with the active export, the export queue and the completed exports.
 */
class SwingExportPanel(
    private val settingsPreferences: ExportSettingsPreferences,
    private val renderService: RenderService,
    // The adjustments of the open project. The export takes a copy when the user queues it.
    private val adjustments: AdjustmentsSession,
    private val completedRepository: CompletedRendersRepository,
    private val filePicker: FilePicker,
    private val dialogs: UserDialogService,
    encoderCapabilities: CompletableFuture<EncoderCapabilities>,
    private val hints: HintController = HintController.NONE,
) : JPanel(BorderLayout()), AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val closed = AtomicBoolean(false)
    private var queueSubscription: AutoCloseable? = null

    /** Called on the EDT with the number of running and queued exports, after each change of the queue. */
    var onActiveExportCountChanged: ((Int) -> Unit)? = null

    /** The number of running and queued exports in the last snapshot of the queue. */
    val activeExportCount: Int get() = activeCount(lastSnapshot)

    /**
     * Expired mode (build-expiry-spec.md, "Expired mode"): the start button is gray, and a click calls
     * [onNewExportRefused] in place of the start of an export. The queue and the completed exports work as usual.
     */
    var newExportRefused = false
        set(value) {
            field = value
            initButton.blocked = value
        }

    /** Called on the EDT when the user clicks the start button in expired mode. */
    var onNewExportRefused: (() -> Unit)? = null

    /** Opens the feedback form for an export error (T3 of B-8) with the title and the text of the error dialog. */
    var onReportProblem: ((title: String, message: String) -> Unit)? = null

    private fun showReportableError(message: String, title: String) {
        val report = onReportProblem
        dialogs.showReportableError(this, message, title, report?.let { { it(title, message) } })
    }

    fun onActivated() {
        // Ensure Completed list reflects latest persisted items (global across projects)
        refreshCompletedFromStore()
        // Refresh points summary and button gating on activation to reflect current project context
        updatePointsSummary()
        updateInitButtonState()
        updateScoreboardDefault()
        updateCommentsDefault()
        updateStatsCardDefault()
        updateSetSummariesDefault()
    }
    // Project context (manifest path) — optional; user can still pick output file.
    private var manifestPath: String? = null
    // Keep last observed snapshot to expose Details dialog
    private var lastSnapshot: ActiveQueueSnapshot? = null
    // The source video that was probed last, so that a tab switch does not run ffprobe again.
    private var probedSourcePath: String? = null
    private var sourceInfo = ExportSourceInfo.UNKNOWN

    // Left controls: content of the video
    private val contentCards = ExportContentCards()
    private val fullVideoCard = contentCards.fullVideo.apply {
        toolTipText = "Export the complete source video, with the time between points."
    }
    private val pointsCard = contentCards.points.apply {
        toolTipText = "Export only the marked points. The time between points is cut."
    }
    private val favoritesCard = contentCards.favorites
    private var lastContentCard: ChoiceCard = pointsCard
    private val scoreboardCheck = IncludeTile("export-scoreboard", "Scoreboard").apply {
        toolTipText = "Burn in a scoreboard overlay that updates after each point. Uses the data of the Scoring tab."
    }
    private val commentsCheck = IncludeTile("export-comments", "Comments").apply {
        toolTipText = "Burn the comments from the Points tab into the video as centered lower-third text."
    }
    private val statsCardCheck = IncludeTile("export-stats-card", "Statistics card").apply {
        toolTipText = "Add a card with the match statistics after the last point. Select the statistics in the Stats tab."
    }
    private val setSummariesCheck = IncludeTile("export-set-summaries", "Set summaries").apply {
        toolTipText = "Add a card with the statistics of each set after the last point of the set. Select the statistics in the Stats tab."
    }
    private val qualityPanel = ExportQualityPanel(settingsPreferences)
    private val initButton = StartExportButton(START_EXPORT).apply {
        name = "export-initialize"
        addActionListener { onInitializeRender() }
    }
    private val footer = ExportFooter(initButton)
    // The left column: the export settings, or a notice when no project is open
    private val leftCards = JPanel(CardLayout())

    // Right side: one table with the active export, the queue and the completed exports
    private val exportsTable = ExportsTable(renderService, completedRepository, dialogs, onCancelActive = { cancelActiveExport() })
    private var lastFailureNotifiedJobId: String? = null

    init {
        background = Palette.BG

        // Left configuration column: the steps scroll, the footer stays at the bottom.
        val checks = GridRows(2, 6, 6).apply {
            add(scoreboardCheck)
            add(commentsCheck)
            add(statsCardCheck)
            add(setSummariesCheck)
        }
        val steps = ScrollableStack(0).apply {
            border = EmptyBorder(18, 20, 20, 20)
            add(StepHeader(1, "Content"))
            add(VGap(10))
            add(contentCards)
            add(VGap(22))
            add(StepHeader(2, "Include in the video"))
            add(VGap(10))
            add(checks)
            add(VGap(22))
            add(StepHeader(3, "Quality and file size", qualityPanel.modeControl))
            add(VGap(10))
            add(qualityPanel)
        }
        val scroll = JScrollPane(steps).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            applyDarkScrollbar(this, Palette.CARD)
        }
        val settings = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(scroll, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
        val left = leftCards.apply {
            background = Palette.CARD
            border = BorderFactory.createMatteBorder(0, 0, 0, 1, Palette.LINE)
            preferredSize = Dimension(LEFT_WIDTH, 10)
            minimumSize = Dimension(LEFT_WIDTH, 10)
            add(settings, LEFT_SETTINGS)
            add(NoProjectNotice(), LEFT_NO_PROJECT)
        }
        showLeftCard()

        add(left, BorderLayout.WEST)
        add(exportsTable, BorderLayout.CENTER)

        // Load the saved completed exports.
        refreshCompletedFromStore()
        qualityPanel.onChange = { updateFooter() }

        updatePointsSummary()
        updateFavoriteOnlyAvailability()

        listOf(fullVideoCard, pointsCard).forEach { card ->
            card.addActionListener {
                lastContentCard = card
                onContentChanged()
            }
        }
        favoritesCard.addActionListener {
            if (validFavoriteCount() <= 0) {
                lastContentCard.isSelected = true
                dialogs.showWarning(
                    this,
                    "Only favorites needs at least one valid favorite point. Mark a point with a star on the Points tab.",
                    "Favorite export unavailable",
                )
            } else {
                lastContentCard = favoritesCard
            }
            onContentChanged()
        }
        scoreboardCheck.addActionListener {
            // If user tries to enable scoreboard with no scored points, prevent and explain
            if (scoreboardCheck.isSelected && !hasAnyScoredPoints()) {
                scoreboardCheck.isSelected = false
                dialogs.showWarning(this, "Cannot include scoreboard: there are no scored points in the current project.", "Scoreboard unavailable")
            }
        }
        commentsCheck.addActionListener {
            // Keep the choice of the user for this project. It replaces the default.
            currentProjectDir()?.let { settingsPreferences.saveIncludeComments(it, commentsCheck.isSelected) }
        }
        statsCardCheck.addActionListener {
            if (statsCardCheck.isSelected && currentStatsCard() == null) {
                statsCardCheck.isSelected = false
                dialogs.showWarning(
                    this,
                    "Cannot include the statistics card: no selected statistic has a value. Select the statistics in the Stats tab.",
                    "Statistics card unavailable",
                )
            }
            currentProjectDir()?.let { settingsPreferences.saveIncludeStatsCard(it, statsCardCheck.isSelected) }
            updateOutputDuration()
        }
        setSummariesCheck.addActionListener {
            currentProjectDir()?.let { settingsPreferences.saveIncludeSetSummaries(it, setSummariesCheck.isSelected) }
            updateOutputDuration()
        }

        // The encoder detection runs test encodes in the background. Show its result when it is ready.
        val detection = encoderCapabilities.exceptionally { EncoderCapabilities.NONE }
        val detected = detection.getNow(null)
        if (detected != null) {
            qualityPanel.setEncoders(detected)
        } else {
            detection.thenAccept { capabilities ->
                SwingUtilities.invokeLater { if (!closed.get()) qualityPanel.setEncoders(capabilities) }
            }
        }

        // Observe queue updates to refresh UI
        queueSubscription = renderService.observe { snap ->
            SwingUtilities.invokeLater {
                lastSnapshot = snap
                onActiveExportCountChanged?.invoke(activeCount(snap))
                updateStartButtonText(snap)
                exportsTable.showSnapshot(snap)
                val cur = snap.current
                if (cur?.status == RenderStatus.RUNNING) showExportQueueHint()
                if (cur?.status == RenderStatus.COMPLETED) refreshCompletedFromStore()
                if (cur?.status == RenderStatus.FAILED && lastFailureNotifiedJobId != cur.id) {
                    lastFailureNotifiedJobId = cur.id
                    val message = listOfNotNull(cur.failureReason ?: "Unknown error", ExportFailureAdvice.of(cur)).joinToString("\n\n")
                    showReportableError(message, "Export failed")
                }
            }
        }
        updateFooter()
    }

    /** While an export runs: the user can continue to edit, and more exports go into a queue. */
    private fun showExportQueueHint() {
        if (!isShowing) return
        hints.show(HintId.EXPORT_QUEUE, exportsTable.activeExportRow, EXPORT_QUEUE_HINT, HintBalloon.Placement.ABOVE)
    }

    private fun cancelActiveExport() {
        if (dialogs.confirm(
                this,
                "Cancel the current export? The partly written file is deleted.",
                "Cancel export",
                confirmLabel = "Cancel export",
                cancelLabel = "Keep exporting",
                destructive = true,
            )
        ) {
            renderService.cancelCurrent()
        }
    }

    fun setProjectManifest(path: String?) {
        manifestPath = path
        showLeftCard()
        refreshSourceInfo()
        updatePointsSummary()
        updateFavoriteOnlyAvailability()
        updateInitButtonState()
        updateScoreboardDefault()
        updateCommentsDefault()
    }

    /** The export settings need an open project. Without a project, the left column shows a notice. */
    private fun showLeftCard() {
        val card = if (manifestPath.isNullOrBlank()) LEFT_NO_PROJECT else LEFT_SETTINGS
        (leftCards.layout as CardLayout).show(leftCards, card)
    }

    private fun idleTrimSelected(): Boolean = !fullVideoCard.isSelected

    private fun favoriteOnlySelected(): Boolean = favoritesCard.isSelected

    private fun onContentChanged() {
        updateSetSummariesState()
        updateOutputDuration()
        updateInitButtonState()
        updateFooter()
    }

    /** The footer shows the estimated file size, the content and the video settings of the next export. */
    private fun updateFooter() {
        val content = RenderFormatting.formatCutMode(idleTrim = idleTrimSelected(), favoriteOnly = favoriteOnlySelected())
        footer.show(qualityPanel.selectedSize(), listOf(content, qualityPanel.selectedVideoSummary()).filter { it.isNotEmpty() }.joinToString(" · "))
    }

    private fun onInitializeRender() {
        if (newExportRefused) {
            onNewExportRefused?.invoke()
            return
        }
        val readiness = initializationReadiness()
        if (!readiness.enabled) {
            dialogs.showWarning(
                this,
                readiness.disabledReason ?: "Export cannot start right now.",
                INIT_BLOCKED_TITLE,
            )
            updateFavoriteOnlyAvailability()
            updateInitButtonState()
            return
        }

        // Resolve manifest and source video
        val manifestPath = this.manifestPath
        val manifest = try {
            if (manifestPath.isNullOrBlank()) null else ManifestIO.read(manifestPath)
        } catch (t: Throwable) {
            showReportableError(t.message ?: t.toString(), "Failed to read manifest")
            return
        }
        val source = manifest?.sourceVideo
        if (source.isNullOrBlank() || !File(source).exists()) {
            dialogs.showError(this, "Select source video", "Source video missing")
            return
        }

        val projectDir = if (manifestPath != null) EdlIO.projectDirFromManifest(manifestPath) else null
        val edl = try { if (projectDir != null) EdlIO.readForProjectDir(projectDir) else null } catch (_: Throwable) { null }
        val allValidPoints = ExportPlanner.validateEdl(edl)
        val idleTrim = idleTrimSelected()
        val favoriteOnly = favoriteOnlySelected()
        val keeps: List<PointV1> = ExportPlanner.selectedKeepPoints(
            validPoints = allValidPoints,
            idleTrim = idleTrim,
            favoriteOnly = favoriteOnly,
        )
        if (favoriteOnly && keeps.isEmpty()) {
            dialogs.showWarning(this, "Cannot export only favorite points: no valid favorite points are available.", "Favorite export unavailable")
            updateFavoriteOnlyAvailability()
            updateInitButtonState()
            return
        }
        if (idleTrim && keeps.isEmpty()) {
            if (!dialogs.confirm(this, "The project has no valid points. Export the full video?", "No points", confirmLabel = "Export full video")) return
        }
        val quality = qualityPanel.selection()
        val target = quality.target
        val presets = ExportPresetsIO.load()
        val selPreset = presets.firstOrNull { it.id == target.presetId } ?: presets[ExportPresetsIO.defaultBalancedIndex(presets)]

        // Choose output path
        val initialDir = settingsPreferences.loadOutputDirectory()
            ?: projectDir?.let { File(it) }
            ?: File(source).parentFile
        val suggestedFile = File(
            initialDir,
            ExportPlanner.suggestFilename(
                projectName = manifest?.name ?: File(source).nameWithoutExtension,
                contentLabel = ExportPlanner.contentLabel(keeps, favoriteOnly),
                presetId = selPreset.id,
                resolutionLabel = target.resolution.label,
            )
        )
        val chosen = filePicker.chooseExportDestination(
            parent = this,
            title = "Save Export As…",
            initialDirectory = initialDir,
            suggestedFile = suggestedFile,
        ) ?: return
        settingsPreferences.saveOutputDirectory(chosen)
        // Ensure extension if user omitted
        val defaultExt = selPreset.container?.format?.lowercase()?.let { if (it.startsWith(".")) it.drop(1) else it } ?: "mp4"
        val out = ExportPlanner.ensureExtension(chosen, defaultExt)

        // The file dialog asked to replace the file that the user selected. Ask here only for a name that the app changed.
        if (out != chosen && out.exists()) {
            if (!dialogs.confirm(this, "File exists. Overwrite?\n\n${out.path}", "Confirm overwrite", confirmLabel = "Overwrite", destructive = true)) return
        }

        val score = try {
            if (projectDir != null) ScoreIO.readForProjectDir(projectDir) else ScoreV1()
        } catch (_: Throwable) {
            ScoreV1()
        }
        val plan = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest,
                sourcePath = source,
                edl = edl,
                score = score,
                preset = selPreset,
                resolution = target.resolution,
                outputFrameRate = target.frameRate?.ffmpegArgument,
                videoBitrateK = target.bitrateK,
                encoderLabel = quality.encoder.jobLabel,
                idleTrim = idleTrim,
                favoriteOnly = favoriteOnly,
                includeScoreboard = scoreboardCheck.isSelected,
                includeComments = commentsCheck.isSelected,
                outputPath = out.absolutePath,
                // The file dialog or the prompt above asked the user to replace an existing file.
                replaceableOutputModifiedMs = out.takeIf { it.exists() }?.lastModified(),
                sourceDurationMs = sourceInfo.durationMs,
                includeStatsCard = statsCardCheck.isSelected,
                includeSetSummaries = setSummariesCheck.isEnabled && setSummariesCheck.isSelected,
                statsSettings = readCurrentStatsSettings(),
                adjustments = adjustments.get(),
            )
        )

        val queued = hasActiveExports(lastSnapshot)
        renderService.enqueue(plan.job)
        dialogs.showInfo(
            this,
            if (queued) "Export queued: ${out.name}. It starts when the current exports end." else "Export started: ${out.name}",
            if (queued) "Export queued" else "Export started",
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        queueSubscription?.close()
        queueSubscription = null
    }

    /** Reads the size, frame rate, bitrate and duration of the source video with ffprobe. */
    private fun refreshSourceInfo() {
        val sourcePath = try {
            manifestPath?.let(ManifestIO::read)?.sourceVideo
        } catch (_: Throwable) {
            null
        }?.takeIf { File(it).isFile }
        if (sourcePath != null && sourcePath == probedSourcePath) return
        probedSourcePath = sourcePath
        sourceInfo = sourcePath
            ?.let { ExportSourceProbe.probe(it, ApplicationLayout.current().ffprobeExecutable) }
            ?: ExportSourceInfo.UNKNOWN
        qualityPanel.setSource(sourceInfo)
    }

    /** The duration of the exported video, for the file size estimates. */
    private fun updateOutputDuration() {
        val durationMs = if (!idleTrimSelected()) {
            sourceInfo.durationMs
        } else {
            val validPoints = ExportPlanner.validateEdl(readCurrentProjectEdl())
            val kept = ExportPlanner.selectedKeepPoints(validPoints, idleTrim = true, favoriteOnly = favoriteOnlySelected())
            // Without valid points the export falls back to the full video.
            if (kept.isEmpty()) sourceInfo.durationMs else ExportChunkPlanner.keptDurationMs(kept)
        }
        val cardMs = if (statsCardCheck.isSelected) currentStatsCard()?.durationMs ?: 0L else 0L
        val setCardsMs = if (setSummariesCheck.isEnabled && setSummariesCheck.isSelected) {
            currentSetSummaries().sumOf { it.card.durationMs }
        } else {
            0L
        }
        qualityPanel.setOutputDurationMs(durationMs?.plus(cardMs + setCardsMs))
    }

    private fun refreshCompletedFromStore() {
        exportsTable.refreshCompletedFromStore()
    }

    private fun validFavoriteCount(): Int {
        return try {
            loadExportSummary().favoriteCount
        } catch (_: Throwable) {
            0
        }
    }

    private fun updateFavoriteOnlyAvailability() {
        val available = manifestPath != null
        favoritesCard.isEnabled = available
        if (!available && favoritesCard.isSelected) {
            pointsCard.isSelected = true
            lastContentCard = pointsCard
        }
        favoritesCard.toolTipText = when {
            !available -> "Requires an open project."
            validFavoriteCount() > 0 -> "Export only the points that are marked with a star."
            else -> "No valid favorite points are available. Mark a point with a star on the Points tab."
        }
    }

    private fun hasAnyScoredPoints(): Boolean {
        return try {
            readCurrentProjectScore().outcomes.values.any { it == Outcome.P1 || it == Outcome.P2 }
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateScoreboardDefault() {
        scoreboardCheck.isSelected = hasAnyScoredPoints()
    }

    /** Selects the checkbox if the project has comments. A saved choice of the user has priority. */
    private fun updateCommentsDefault() {
        val saved = currentProjectDir()?.let(settingsPreferences::loadIncludeComments)
        commentsCheck.isSelected = saved ?: hasAnyComments()
    }

    /**
     * Selects the checkbox if the project has a statistics card. A saved choice of the user has priority.
     * The note shows how long the card is, or why the export has no card.
     */
    private fun updateStatsCardDefault() {
        val card = currentStatsCard()
        statsCardCheck.setNote(
            when {
                card != null -> "${card.durationMs / 1000} s, " + if (card.pages.size == 1) "1 page" else "${card.pages.size} pages"
                hasAnyScoredPoints() -> "No selected statistics"
                else -> "No scored points"
            },
        )
        val saved = currentProjectDir()?.let(settingsPreferences::loadIncludeStatsCard)
        statsCardCheck.isSelected = card != null && (saved ?: true)
        updateOutputDuration()
    }

    /** The statistics card of the current project at 1080p, or null when the export cannot have a card. */
    private fun currentStatsCard(): StatsCardVideo? = try {
        StatsCardVideo.of(readCurrentProjectEdl(), readCurrentProjectScore(), readCurrentStatsSettings(), 1920, 1080)
    } catch (t: Throwable) {
        logger.warn(t) { "Export: cannot make the statistics card" }
        null
    }

    /** Selects the checkbox from the saved choice of the user. Set summaries are off by default. */
    private fun updateSetSummariesDefault() {
        val saved = currentProjectDir()?.let(settingsPreferences::loadIncludeSetSummaries)
        setSummariesCheck.isSelected = saved ?: false
        updateSetSummariesState()
        updateOutputDuration()
    }

    /**
     * Enables the checkbox when the selected content has at least one set card.
     * The note shows how long the set cards are, or why the export has no set cards.
     */
    private fun updateSetSummariesState() {
        val summaries = currentSetSummaries()
        setSummariesCheck.isEnabled = summaries.isNotEmpty()
        setSummariesCheck.setNote(
            when {
                !idleTrimSelected() -> "Only for Only points and Only favorites"
                summaries.isNotEmpty() -> {
                    val seconds = summaries.sumOf { it.card.durationMs } / 1000
                    (if (summaries.size == 1) "1 set" else "${summaries.size} sets") + ", $seconds s"
                }
                !hasAnyScoredPoints() -> "No scored points"
                currentStatsCard() == null -> "No selected statistics"
                else -> "No completed set in the video"
            },
        )
    }

    /** The set cards of the selected content at 1080p. A full video export has no set cards. */
    private fun currentSetSummaries(): List<SetSummaryCard> = try {
        if (!idleTrimSelected()) {
            emptyList()
        } else {
            val edl = readCurrentProjectEdl()
            val kept = ExportPlanner.selectedKeepPoints(
                ExportPlanner.validateEdl(edl),
                idleTrim = true,
                favoriteOnly = favoriteOnlySelected(),
            )
            ExportPlanner.setSummaryCards(edl, readCurrentProjectScore(), readCurrentStatsSettings(), kept, 1920, 1080)
        }
    } catch (t: Throwable) {
        logger.warn(t) { "Export: cannot make the set cards" }
        emptyList()
    }

    private fun readCurrentStatsSettings(): StatsSettingsV1 = try {
        currentProjectDir()?.let(StatsIO::readForProjectDir) ?: StatsSettingsV1()
    } catch (t: Throwable) {
        logger.warn(t) { "Export: cannot read stats.json" }
        StatsSettingsV1()
    }

    private fun hasAnyComments(): Boolean =
        readCurrentProjectEdl()?.comments.orEmpty().any { it.text.isNotBlank() }

    private fun currentProjectDir(): String? {
        val mp = manifestPath
        if (mp.isNullOrBlank()) return null
        return try {
            EdlIO.projectDirFromManifest(mp)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadExportSummary() = ExportPlanner.summarize(
        readCurrentProjectEdl(),
        readCurrentProjectScore(),
    )

    private fun readCurrentProjectEdl(): EdlV1? {
        val projectDir = currentProjectDir()
        if (projectDir == null) {
            logger.info { "Export summary: no project directory (manifestPath=$manifestPath)" }
            return null
        }
        return try {
            EdlIO.readForProjectDir(projectDir).also {
                logger.info { "Export summary: read ${it.points.size} points from ${EdlIO.edlFilePath(projectDir)}" }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Export summary: failed to read EDL from ${EdlIO.edlFilePath(projectDir)}" }
            null
        }
    }

    private fun readCurrentProjectScore(): ScoreV1 {
        val projectDir = currentProjectDir()
        return try {
            if (projectDir != null) ScoreIO.readForProjectDir(projectDir) else ScoreV1()
        } catch (t: Throwable) {
            logger.warn(t) { "Export summary: failed to read score for $projectDir" }
            ScoreV1()
        }
    }

    private fun showScoredCount(summary: ExportPointSummary?) {
        scoreboardCheck.setNote(
            when {
                summary == null -> ""
                summary.pointCount == 0 -> "No points"
                else -> "${summary.scoredCount}/${summary.pointCount} points scored"
            },
            ok = summary?.allScored == true,
        )
    }

    private fun updatePointsSummary() {
        val summary = try {
            if (manifestPath == null) null else loadExportSummary()
        } catch (_: Throwable) {
            null
        }
        contentCards.show(summary, sourceInfo.durationMs.takeIf { manifestPath != null })
        showScoredCount(summary)
        updateFavoriteOnlyAvailability()
        updateOutputDuration()
        updateFooter()
    }

    // Task 3.15 — Read project context and prerequisites for the start button
    private fun initializationReadiness(): ExportReadiness = try {
        val mp = manifestPath
        val manifest = try { mp?.let(ManifestIO::read) } catch (_: Throwable) { null }
        val validPoints = if (mp.isNullOrBlank()) {
            emptyList()
        } else {
            val projectDir = EdlIO.projectDirFromManifest(mp)
            ExportPlanner.validateEdl(try { EdlIO.readForProjectDir(projectDir) } catch (_: Throwable) { null })
        }
        ExportPlanner.initializationReadiness(
            hasProject = !mp.isNullOrBlank(),
            sourceVideoExists = manifest?.sourceVideo?.let { File(it).isFile } == true,
            idleTrim = idleTrimSelected(),
            favoriteOnly = favoriteOnlySelected(),
            validPoints = validPoints,
        )
    } catch (_: Throwable) {
        ExportReadiness(false, "Export is not available because of an unexpected error.")
    }

    // The button stays clickable even when an export cannot start; clicking it explains why.
    private fun updateInitButtonState() {
        val readiness = initializationReadiness()
        initButton.isEnabled = true
        initButton.toolTipText = readiness.disabledReason ?: INIT_BUTTON_TOOLTIP
        initButton.accessibleContext.accessibleDescription = readiness.disabledReason
    }

    /** "Start export" when nothing runs, "Enqueue export" when an export runs or waits in the queue. */
    private fun updateStartButtonText(snapshot: ActiveQueueSnapshot?) {
        val text = if (hasActiveExports(snapshot)) ENQUEUE_EXPORT else START_EXPORT
        if (initButton.text != text) {
            initButton.text = text
            initButton.repaint()
        }
    }

    private fun hasActiveExports(snapshot: ActiveQueueSnapshot?): Boolean = activeCount(snapshot) > 0

    private fun activeCount(snapshot: ActiveQueueSnapshot?): Int = snapshot?.activeCount ?: 0

    private companion object {
        const val LEFT_WIDTH = 440
        const val LEFT_SETTINGS = "settings"
        const val LEFT_NO_PROJECT = "no-project"
        const val START_EXPORT = "Start export"
        const val ENQUEUE_EXPORT = "Enqueue export"
        const val INIT_BUTTON_TOOLTIP = "Choose an output file and start the export."
        const val EXPORT_QUEUE_HINT = "You can continue to edit while the export runs. More exports can go into a queue."
        const val INIT_BLOCKED_TITLE = "Cannot start export"
    }
}

/** The bottom of the left column: the estimated file size and the settings on the left, the start button on the right. */
/** The left column when no project is open. The export table on the right still shows all exports. */
private class NoProjectNotice : JPanel(GridBagLayout()) {
    init {
        name = "export-no-project"
        isOpaque = false
        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(line("No project is open", ExportUi.font(16f, ExportUi.Weight.BOLD), Palette.FG))
            add(Box.createVerticalStrut(8))
            add(line("Open a project to set up a new export.", ExportUi.font(13f), Palette.FG_2))
        }
        add(text, GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(0, 20, 0, 20)
        })
    }

    // The label fills the width of the column, so that the text has room and does not clip.
    private fun line(text: String, font: Font, color: Color) = JLabel(text, SwingConstants.CENTER).apply {
        this.font = font
        foreground = color
        alignmentX = CENTER_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}

private class ExportFooter(private val button: StartExportButton) : JPanel(null) {
    private val sizeText = WrapText("", ExportUi.font(16f, ExportUi.Weight.BOLD), Palette.FG, lineHeight = 1.45f).apply {
        name = "export-footer-size"
    }
    private val detailText = WrapText("", ExportUi.font(12f), Palette.FG_2, lineHeight = 1.45f).apply {
        name = "export-footer-summary"
    }
    private val summary = Stack(0).apply {
        add(sizeText)
        add(detailText)
    }

    init {
        isOpaque = true
        background = BACKGROUND
        add(summary)
        add(button)
    }

    /** Shows the size, for example "~9.72 GB", and the settings, for example "Only points · 4K · 60 fps". */
    fun show(size: String, details: String) {
        sizeText.setText(if (size == "unknown") "Size unknown" else size, ExportUi.font(16f, ExportUi.Weight.BOLD), Palette.FG)
        detailText.setText(details, ExportUi.font(12f), Palette.FG_2)
        revalidate()
        repaint()
    }

    private fun summaryWidth() = (width - PAD_X * 2 - GAP - BUTTON_WIDTH).coerceAtLeast(0)

    override fun getPreferredSize(): Dimension {
        val summaryHeight = summary.heightForWidth(if (width > 0) summaryWidth() else 176)
        return Dimension(440, PAD_TOP + maxOf(summaryHeight, button.preferredSize.height) + PAD_BOTTOM)
    }

    override fun doLayout() {
        val inner = height - PAD_TOP - PAD_BOTTOM
        val w = summaryWidth()
        val summaryHeight = summary.heightForWidth(w)
        summary.setBounds(PAD_X, PAD_TOP + (inner - summaryHeight) / 2, w, summaryHeight)
        val buttonHeight = button.preferredSize.height
        button.setBounds(width - PAD_X - BUTTON_WIDTH, PAD_TOP + (inner - buttonHeight) / 2, BUTTON_WIDTH, buttonHeight)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = ExportUi.smooth(g)
        try {
            g2.color = Palette.LINE
            g2.fillRect(0, 0, width, 1)
            if (!button.blocked) {
                StartExportButton.paintGlow(g2, button.x, button.y, button.width, button.height)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 20
        const val PAD_TOP = 12
        const val PAD_BOTTOM = 14
        const val GAP = 14
        const val BUTTON_WIDTH = 210
        val BACKGROUND get() = Palette.PANEL
    }
}
