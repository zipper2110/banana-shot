package org.litvin.ui.tabs.points.detect

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.autodetect.DetectImport
import org.litvin.autodetect.DetectOverlayData
import org.litvin.autodetect.DetectResult
import org.litvin.autodetect.DetectRunner
import org.litvin.autodetect.DetectSettings
import org.litvin.autodetect.DetectStage
import org.litvin.autodetect.DetectState
import org.litvin.autodetect.DetectedRally
import org.litvin.autodetect.ImportPlan
import org.litvin.autodetect.SkipReason
import org.litvin.i18n.DisplayFormats
import org.litvin.points.AutoKind
import org.litvin.points.AutoMarkV1
import org.litvin.points.PointV1
import org.litvin.scoring.ScoreReason
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Html
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.PointExtraFields
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Image
import java.awt.Insets
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.text.JTextComponent

/*
 * The UI of the automatic point detection (docs/auto-analysis/app-integration.md).
 *
 * This UI shows only on a PC with the lab (`autodetect.json` in the app data folder). Its texts are English only and
 * are not in the message catalog: `HardCodedTextScanTest` ignores this folder, as it ignores the developer test tab.
 * Put the texts into the catalog when the detection becomes a feature for all users.
 */

/** What the detection UI needs from the Points tab. */
internal interface DetectHost {
    /** The folder of the open project, or null when no project is open. */
    val projectDir: File?

    /** The video of the project when it is one file, or null. The detection does not read a match of several files. */
    val video: File?

    /** The trim of the video in ms, or null when the video has no trim. */
    val trimRangeMs: LongRange?

    /** The names and the colors of the players of the project. */
    val players: ScorePlayers

    /** The clips of the project that came from the detection, in time order. */
    fun autoPoints(): List<PointV1>

    /** The id of the selected clip, or null. */
    val selectedPointId: String?

    /** Selects the clip and plays it from its start. */
    fun selectAndPlay(pointId: String)

    /** Marks the automatic clips as checked by the user. */
    fun markChecked(pointIds: Collection<String>)

    /** Gives each of the clips the other winner. */
    fun swapWinners(pointIds: Set<String>)

    /** Draws [data] on the preview during playback. Null removes it. */
    fun showOverlay(data: DetectOverlayData?)

    /**
     * Writes the pending changes of the tab, adds the clips of [resultFile] to the project, and reads the project
     * again. Returns null when the import failed (the tab then showed the error).
     */
    fun importResult(resultFile: File, settings: DetectSettings, swapped: Boolean, rangeMs: LongRange?): ImportPlan?
}

/** The English texts of the detection UI. */
internal object DetectTexts {
    fun stage(stage: DetectStage?): String = when (stage) {
        null -> "Starting"
        DetectStage.MEDIA -> "Reading the video"
        DetectStage.AUDIO -> "Sound analysis"
        DetectStage.IMAGE -> "Picture analysis"
        DetectStage.COURT -> "Court detection"
        DetectStage.BALL -> "Ball detection"
        DetectStage.FLIGHTS -> "Ball flights"
        DetectStage.POSE -> "Player poses"
        DetectStage.RALLIES -> "Rally detection"
        DetectStage.WINNERS -> "Winners and faults"
    }

    /** A time such as "1 h 05 min" or "42 s". */
    fun duration(seconds: Long): String = when {
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600 / 60).toString().padStart(2, '0')} min"
        seconds >= 60 -> "${seconds / 60} min"
        else -> "$seconds s"
    }

    fun running(state: DetectState.Running, nowMs: Long = System.currentTimeMillis()): String {
        val step = state.stage?.let { "Step ${it.ordinal + 1} of ${DetectStage.entries.size}: " }.orEmpty()
        val percent = state.progress?.let { " ${(it * 100).toInt()} %" }.orEmpty()
        return step + stage(state.stage) + percent + " · " + duration((nowMs - state.startedAtMs) / 1000)
    }

    /** The tooltip of the mark of an automatic clip in the points list. */
    fun rowNote(mark: AutoMarkV1): String {
        val kind = when (mark.kind) {
            AutoKind.RALLY -> "Automatic point"
            AutoKind.SHORT -> "Automatic short point (from a serve; check it)"
            AutoKind.FAULT -> "Automatic serve fault"
        }
        return when {
            mark.kind == AutoKind.FAULT -> kind
            mark.winnerPercent == null -> "$kind · no winner detected"
            else -> "$kind · winner confidence ${mark.winnerPercent} %"
        }
    }

    fun reason(reason: ScoreReason?): String = when (reason) {
        null -> "None"
        ScoreReason.PLAYERS_CALL -> "Players' call"
        ScoreReason.REPLAYED -> "Replayed"
        ScoreReason.SCORE_LOST -> "Score lost"
    }

    fun imported(plan: ImportPlan): String {
        val faults = if (plan.faultCount > 0) ", ${plan.faultCount} serve faults" else ""
        val there = plan.rows.count { it.skipped == SkipReason.OVERLAP }
        val outside = plan.rows.count { it.skipped == SkipReason.OUTSIDE_TRIM }
        val skipped = buildString {
            if (there > 0) append(" Skipped $there: a clip is already there.")
            if (outside > 0) append(" Skipped $outside: outside the trim.")
        }
        return "Added ${plan.rallyCount} points$faults.$skipped"
    }

    fun result(result: DetectResult): String {
        val total = result.stageSeconds.values.sum().toLong()
        val time = if (total > 0) " · run time ${duration(total)}" else ""
        return "Result ready · rallies: ${result.rallies.size}$time"
    }
}

/** True for an automatic clip that the user should check first: no winner, a low confidence, or a short point. */
internal fun AutoMarkV1.isWeak(): Boolean =
    kind == AutoKind.SHORT || (kind == AutoKind.RALLY && (winnerPercent ?: 0) < WEAK_WINNER_PERCENT)

private const val WEAK_WINNER_PERCENT = 70

private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

/** The "Score reason" row of the Edit point dialog. */
internal class ReasonField(current: ScoreReason?, private val onSave: (ScoreReason?) -> Unit) : PointExtraFields {
    private val choice = SegmentedChoice(
        "points-edit-reason",
        (listOf<ScoreReason?>(null) + ScoreReason.entries).map { SegmentedChoice.Option(it, DetectTexts.reason(it)) },
        columns = 2,
    ).apply { selected = current }
    private val before = current
    private var picked = false

    init {
        choice.onChange { picked = true }
    }

    override val rows: List<JComponent> = listOf(
        DialogKit.field("Score differs from the video", choice, note = "why the players counted it differently"),
    )

    override fun save() {
        if (picked && choice.selected != before) onSave(choice.selected)
    }
}

/**
 * The rows under the points list:
 * - the "Detect points" button and the state of a run (the run continues when the user leaves the tab);
 * - the review of the automatic clips: the count of the clips to review, "Accept, next", and "Finish review";
 * - the switch of the detection overlay.
 * The Points tab calls [refresh] when the clips or the project change.
 */
internal class DetectBar(private val runner: DetectRunner, private val host: DetectHost) : Stack(gap = 6) {
    private val button = UiButton("Detect points", Material2AL.INSIGHTS).apply { name = "points-detect" }
    private val status = JLabel().apply {
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
    }
    private val toReview = JLabel().apply {
        name = "points-detect-to-review"
        font = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }
    private val accept = UiButton("Accept, next", buttonHeight = SMALL_BUTTON, fontSize = 11.5f).apply {
        name = "points-detect-accept"
        toolTipText = "Marks the selected automatic point as checked and plays the next point to review ($ACCEPT_KEY)"
    }
    private val finish = UiButton("Finish review", buttonHeight = SMALL_BUTTON, fontSize = 11.5f).apply {
        name = "points-detect-finish"
        toolTipText = "Marks all automatic points as checked. The dots go off."
    }
    private val reviewRow = Stack(gap = 6).apply {
        isOpaque = false
        add(toReview)
        add(JPanel(java.awt.GridLayout(1, 2, 6, 0)).apply {
            isOpaque = false
            add(accept)
            add(finish)
        })
    }
    private val overlay = SwitchBox("Detection overlay", 12f).apply {
        name = "points-detect-overlay"
        toolTipText = "Draws the court model, the ball, the landing points, and the serve speed on the video (debug view)"
    }
    private val ticker = Timer(1000) { render() }
    private var unlisten: (() -> Unit)? = null
    private var overlayFile: File? = null

    init {
        name = "points-detect-bar"
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(8, 12, 8, 12),
        )
        add(JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(button, BorderLayout.WEST)
            add(status, BorderLayout.CENTER)
        })
        add(reviewRow)
        add(overlay)
        button.addActionListener { DetectDialog(this, runner, host).open() }
        accept.addActionListener { acceptAndNext() }
        finish.addActionListener { host.markChecked(host.autoPoints().map { it.id }) }
        overlay.addActionListener { applyOverlay() }
        // The key of "Accept, next". It does nothing while the user types a text.
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(ACCEPT_KEY), "points.detect.accept")
        actionMap.put("points.detect.accept", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) {
                val typing = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner is JTextComponent
                if (!typing && reviewRow.isVisible) acceptAndNext()
            }
        })
    }

    override fun addNotify() {
        super.addNotify()
        unlisten = runner.listen { EventQueue.invokeLater(::refresh) }
        refresh()
    }

    override fun removeNotify() {
        unlisten?.invoke()
        unlisten = null
        ticker.stop()
        super.removeNotify()
    }

    /** Shows the state of the run, the count of the clips to review, and the overlay switch of the open project. */
    fun refresh() {
        val open = host.autoPoints().count { it.auto?.checked == false }
        val showReview = open > 0
        toReview.text = "To review: $open"
        val file = host.projectDir?.let(DetectImport::latestOverlay)
        val showOverlay = file != null
        if (file != overlayFile) {
            // Another project, or a new run: the switch goes off, because the data is different.
            overlayFile = file
            if (overlay.isSelected) {
                overlay.isSelected = false
                host.showOverlay(null)
            }
        }
        if (reviewRow.isVisible != showReview || overlay.isVisible != showOverlay) {
            reviewRow.isVisible = showReview
            overlay.isVisible = showOverlay
            revalidate()
        }
        render()
    }

    private fun render() {
        val state = runner.state
        status.text = when (state) {
            is DetectState.Running -> DetectTexts.running(state)
            is DetectState.Finished -> "Result ready: click to add the points"
            is DetectState.Failed -> if (state.message == null) "Stopped" else "Failed"
            DetectState.Idle -> ""
        }
        status.toolTipText = status.text.takeIf { it.isNotEmpty() }
        if (state is DetectState.Running) ticker.start() else ticker.stop()
    }

    private fun applyOverlay() {
        val file = overlayFile
        if (!overlay.isSelected || file == null) {
            host.showOverlay(null)
            return
        }
        runCatching { DetectOverlayData.read(file) }
            .onSuccess(host::showOverlay)
            .onFailure {
                logger.warn(it) { "Overlay data not read: $file" }
                overlay.isSelected = false
                overlay.toolTipText = "The overlay data does not open: ${it.message}"
                host.showOverlay(null)
            }
    }

    /**
     * Marks the selected automatic clip as checked, and plays the next clip to review after it. After the last clip,
     * it goes to the first clip to review.
     */
    private fun acceptAndNext() {
        val points = host.autoPoints()
        val current = points.firstOrNull { it.id == host.selectedPointId }
        if (current?.auto?.checked == false) host.markChecked(listOf(current.id))
        val open = points.filter { it.auto?.checked == false && it.id != current?.id }
        val next = open.firstOrNull { it.startMs > (current?.startMs ?: -1) } ?: open.firstOrNull()
        next?.let { host.selectAndPlay(it.id) }
    }

    private companion object {
        const val SMALL_BUTTON = 26
        const val ACCEPT_KEY = "T"
    }
}

/**
 * The dialog of the detection: the settings, the state of the run, and one action, "Detect points". The action runs
 * the detection, asks which player is at the near end, and adds the points (decisions 14 and 15).
 */
internal class DetectDialog(parent: Component, private val runner: DetectRunner, private val host: DetectHost) {
    private val dialog = DialogKit.modal(parent, "Detect points")
    private val saved = runner.loadSettings()

    private val scope = SegmentedChoice(
        "detect-scope",
        listOf(
            SegmentedChoice.Option(false, "Rallies only", "fast: about 0.15 × the video length"),
            SegmentedChoice.Option(true, "Rallies and winners", "about 0.65 × the video length"),
        ),
    ).apply { selected = saved.ballPass }
    private val addFaults = SwitchBox("Serve faults (clips in advanced scoring mode)").apply { isSelected = saved.addFaults }
    private val shortPoints = SwitchBox("Short points: ace, double fault").apply { isSelected = saved.shortPoints }
    private val threshold = SegmentedChoice(
        "detect-threshold",
        listOf(
            SegmentedChoice.Option(0.6, "Fewer", "only clear rallies"),
            SegmentedChoice.Option(0.5, "Normal"),
            SegmentedChoice.Option(0.4, "More", "also doubtful ones"),
        ),
    ).apply { selected = listOf(0.6, 0.5, 0.4).minBy { kotlin.math.abs(it - saved.threshold) } }
    private val padBefore = JTextField(seconds(saved.padBeforeMs)).apply { name = "detect-pad-before" }
    private val padAfter = JTextField(seconds(saved.padAfterMs)).apply { name = "detect-pad-after" }
    private val minWinner = SegmentedChoice(
        "detect-min-winner",
        listOf(50, 60, 70, 80).map { SegmentedChoice.Option(it, if (it == 50) "All" else "$it %+") },
    ).apply { selected = listOf(50, 60, 70, 80).minBy { kotlin.math.abs(it - saved.minWinnerPercent) } }
    private val trimOnly = SwitchBox("Only inside the trim of the video").apply {
        name = "detect-trim-only"
        isSelected = saved.trimOnly
    }

    private val status = JLabel(" ").apply {
        font = UiKit.font(12.5f)
        foreground = Palette.FG
    }
    private val detail = DialogKit.errorLine("detect-detail")
    private val start = UiButton("Detect points", Material2MZ.PLAY_ARROW, UiButton.Kind.LIME).apply { name = "detect-start" }
    private val stop = UiButton("Stop", kind = UiButton.Kind.DANGER).apply { name = "detect-stop" }
    private val add = UiButton("Add points").apply {
        name = "detect-add"
        toolTipText = "Adds the points of the last result again, for example with other settings"
    }
    private val swap = UiButton("Swap players").apply {
        name = "detect-swap"
        toolTipText = "Gives each automatic point the other winner (for a wrong answer to \"Who is at the near end?\")"
    }
    private val close = UiButton("Close")
    private val ticker = Timer(1000) { render() }
    private var unlisten: (() -> Unit)? = null

    /** The result file that "Add points" uses: of this run, or the newest one of the project. */
    private var resultFile: File? = null

    /** True while the dialog adds the points of a result, so that a second state event does not add them again. */
    private var adding = false

    fun open() {
        build()
        dialog.setLocationRelativeTo(dialog.owner)
        // A run that ended while the dialog was closed continues here with the player question.
        EventQueue.invokeLater(::continueAfterRun)
        DialogKit.showModal(dialog)
    }

    /** Builds the packed dialog without showing it. */
    internal fun build(): javax.swing.JDialog {
        val detect = DialogGroup("What to detect", Material2AL.INSIGHTS).apply {
            wide(scope)
            wide(addFaults)
            wide(shortPoints)
            row("Rallies", threshold)
        }
        val clips = DialogGroup("Clips", Material2AL.ADD).apply {
            row("Seconds before", DialogKit.inputBox(padBefore))
            row("Seconds after", DialogKit.inputBox(padAfter))
            row("Set winners", minWinner)
            wide(trimOnly)
        }
        val state = Stack(gap = 2).apply {
            add(status)
            add(detail)
        }
        val body = Stack(pad = Insets(8, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 12).apply {
            add(detect)
            add(clips)
            add(state)
        }
        dialog.contentPane = DialogKit.content(
            DialogKit.WIDE,
            DialogKit.head("Detect points", "lab feature · singles, one video file"),
            DialogKit.scrollBody(body),
            DialogKit.footer(left = listOf(stop, swap), right = listOf(close, add, start)),
        )
        scope.onChange { syncScope() }
        start.addActionListener { startRun() }
        stop.addActionListener { runner.stop() }
        add.addActionListener { addPoints() }
        swap.addActionListener { swapPlayers() }
        close.addActionListener { dialog.dispose() }
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent?) {
                unlisten?.invoke()
                ticker.stop()
                runner.saveSettings(settings() ?: saved)
            }
        })
        unlisten = runner.listen {
            EventQueue.invokeLater {
                render()
                continueAfterRun()
            }
        }
        syncScope()
        render()
        DialogKit.packToScreen(dialog)
        return dialog
    }

    /** The winner settings work only with the ball analysis. */
    private fun syncScope() {
        val winners = scope.selected == true
        addFaults.isEnabled = winners
        shortPoints.isEnabled = winners
        minWinner.isEnabled = winners
    }

    private fun seconds(ms: Int): String = DisplayFormats.decimal(ms / 1000.0, 1)

    /** The settings of the controls, or null when a time does not parse (the dialog then shows the error). */
    private fun settings(): DetectSettings? {
        val before = DisplayFormats.parseDecimal(padBefore.text)
        val after = DisplayFormats.parseDecimal(padAfter.text)
        if (before == null || after == null) {
            say("Type the seconds as a number, for example 1.5", Palette.RED)
            return null
        }
        return DetectSettings(
            ballPass = scope.selected == true,
            threshold = threshold.selected ?: 0.5,
            shortPoints = shortPoints.isSelected,
            padBeforeMs = (before * 1000).toInt(),
            padAfterMs = (after * 1000).toInt(),
            addWinners = true,
            minWinnerPercent = minWinner.selected ?: 50,
            addFaults = addFaults.isSelected,
            trimOnly = trimOnly.isSelected,
        ).normalized()
    }

    private fun say(text: String, color: java.awt.Color = Palette.FG_2) {
        detail.foreground = color
        detail.text = text
    }

    private fun startRun() {
        val settings = settings() ?: return
        val dir = host.projectDir
        val video = host.video
        if (dir == null || video == null) {
            say("The detection needs a project with one video file.", Palette.RED)
            return
        }
        runner.saveSettings(settings)
        say(" ")
        if (!runner.start(dir, dir.name, video, settings)) say("A detection is already running.", Palette.RED)
    }

    /** After a run for this project ended with a result: asks the player question and adds the points. */
    private fun continueAfterRun() {
        val state = runner.state as? DetectState.Finished ?: return
        if (state.projectDir != host.projectDir || !dialog.isDisplayable) return
        resultFile = state.resultFile
        addPoints()
    }

    private fun addPoints() {
        if (adding) return
        val settings = settings() ?: return
        val file = resultFile ?: return
        val dir = host.projectDir ?: return
        adding = true
        try {
            runner.saveSettings(settings)
            val result = runCatching { DetectResult.read(file) }.getOrElse {
                say("The result file does not open: ${it.message}", Palette.RED)
                return
            }
            val range = host.trimRangeMs.takeIf { settings.trimOnly }
            val rally = DetectImport.questionRally(result, range)
            val swapped = if (rally == null) {
                false
            } else {
                val picture = runner.frameFile(dir.name, rally.start.toInt() + 1)
                val near = PlayerQuestion.ask(dialog, picture, rally, host.players)
                if (near == null) {
                    say("No points added. \"Add points\" asks again.")
                    return
                }
                DetectImport.isSwapped(rally, near)
            }
            val plan = host.importResult(file, settings, swapped, range) ?: return
            say(DetectTexts.imported(plan))
            runner.reset()
        } finally {
            adding = false
            render()
        }
    }

    private fun swapPlayers() {
        val ids = host.autoPoints().map { it.id }.toSet()
        host.swapWinners(ids)
        say("The winners of the automatic points are swapped.")
    }

    private fun render() {
        val state = runner.state
        val dir = host.projectDir
        val running = state is DetectState.Running
        val mine = when (state) {
            is DetectState.Running -> state.projectDir == dir
            is DetectState.Finished -> state.projectDir == dir
            is DetectState.Failed -> state.projectDir == dir
            DetectState.Idle -> true
        }
        resultFile = (state as? DetectState.Finished)?.takeIf { mine }?.resultFile ?: dir?.let(DetectImport::latestResult)
        status.text = when {
            state is DetectState.Running && !mine -> "A detection runs for the project \"${state.projectDir.name}\"."
            state is DetectState.Running -> DetectTexts.running(state)
            state is DetectState.Failed && mine && state.message != null -> "The detection failed: ${state.message}"
            state is DetectState.Failed && mine -> "The detection was stopped. A new start continues after the last complete step."
            else -> resultFile?.let { file -> runCatching { DetectTexts.result(DetectResult.read(file)) }.getOrNull() }
                ?: "No result for this project yet. The run continues when you close this dialog."
        }
        if (state is DetectState.Failed && mine && state.logFile != null) status.toolTipText = state.logFile.absolutePath
        // A warning of the result, for example a court model that is not usable. Another message has priority.
        val warning = resultFile?.takeIf { !running }?.let { file -> runCatching { DetectResult.read(file).warnings.firstOrNull() }.getOrNull() }
        if (warning != null && detail.text.isBlank()) say(Html.wrappedTooltip(warning, 380), Palette.YELLOW)
        start.isEnabled = !running
        stop.isVisible = running && mine
        add.isVisible = !running && resultFile != null
        swap.isVisible = !running && host.autoPoints().isNotEmpty()
        if (running) ticker.start() else ticker.stop()
    }
}

/**
 * The question after a run: "Who is at the near end?" The detection calls the player at the near end of the first
 * rally "player 1", and it follows that player through the end changes. The answer gives its players the names of
 * the project (decision 15).
 */
internal object PlayerQuestion {
    private const val PICTURE_WIDTH = 500

    /** Shows the question. Returns the player of the project (1 or 2) at the near end in [rally], or null for no answer. */
    fun ask(parent: Component, picture: File?, rally: DetectedRally, players: ScorePlayers): Int? {
        var answer: Int? = null
        val dialog = build(parent, picture, rally, players) { answer = it }
        dialog.setLocationRelativeTo(parent)
        DialogKit.showModal(dialog)
        return answer
    }

    /** Builds the packed dialog without showing it. [onAnswer] gets the clicked player. */
    internal fun build(parent: Component, picture: File?, rally: DetectedRally, players: ScorePlayers, onAnswer: (Int) -> Unit): javax.swing.JDialog {
        val dialog = DialogKit.modal(parent, "Who is at the near end?")
        dialog.name = "detect-player-question"
        val image = picture?.let { file -> runCatching { ImageIO.read(file) }.getOrNull() }
        val time = Timecode.format((rally.start * 1000).toLong())
        val body = Stack(pad = Insets(8, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 10).apply {
            if (image != null) add(Picture(image, PICTURE_WIDTH))
            add(JLabel(Html.wrappedTooltip(
                "This is the first detected rally (at $time). Which player is at the near end, the one closer to the camera?",
                PICTURE_WIDTH,
            )).apply {
                font = UiKit.font(13f)
                foreground = Palette.FG
            })
        }
        val buttons = (1..2).map { player ->
            UiButton(players.name(player)).apply {
                name = "detect-near-player-$player"
                icon = Dot(players.color(player))
                addActionListener {
                    onAnswer(player)
                    dialog.dispose()
                }
            }
        }
        val cancel = UiButton("Cancel").apply { addActionListener { dialog.dispose() } }
        dialog.contentPane = DialogKit.content(
            DialogKit.WIDE,
            DialogKit.head("Who is at the near end?"),
            body,
            DialogKit.footer(left = listOf(cancel), right = buttons),
        )
        DialogKit.packToScreen(dialog)
        return dialog
    }

    /** A picture of the video, scaled to [shownWidth]. */
    private class Picture(private val image: Image, private val shownWidth: Int) : JComponent() {
        private val shownHeight = (shownWidth.toDouble() * image.getHeight(null) / image.getWidth(null).coerceAtLeast(1)).toInt()

        override fun getPreferredSize() = Dimension(shownWidth, shownHeight)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.drawImage(image, 0, 0, shownWidth, shownHeight, null)
            } finally {
                g2.dispose()
            }
        }
    }

    /** The color of a player as a round icon. */
    private class Dot(private val color: java.awt.Color) : javax.swing.Icon {
        override fun getIconWidth() = 12
        override fun getIconHeight() = 12
        override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = UiKit.smooth(g)
            try {
                g2.color = color
                g2.fillOval(x + 1, y + 1, 10, 10)
            } finally {
                g2.dispose()
            }
        }
    }
}
