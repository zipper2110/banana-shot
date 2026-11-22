package org.litvin.ui.tabs.points.detect

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.autodetect.DetectImport
import org.litvin.autodetect.DetectResult
import org.litvin.autodetect.DetectRunner
import org.litvin.autodetect.DetectSettings
import org.litvin.autodetect.DetectStage
import org.litvin.autodetect.DetectState
import org.litvin.autodetect.ImportPlan
import org.litvin.i18n.DisplayFormats
import org.litvin.points.AutoKind
import org.litvin.points.AutoMarkV1
import org.litvin.scoring.ScoreReason
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.PointExtraFields
import java.awt.BorderLayout
import java.awt.Component
import java.awt.EventQueue
import java.awt.Insets
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.Timer

/*
 * The UI of the automatic point detection (docs/auto-analysis/app-integration.md, iteration 1).
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

    /**
     * Writes the pending changes of the tab, adds the clips of [resultFile] to the project, and reads the project
     * again. Returns null when the import failed (the tab then showed the error).
     */
    fun importResult(resultFile: File, settings: DetectSettings): ImportPlan?
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
        val skipped = if (plan.skippedCount > 0) " ${plan.skippedCount} were skipped (a clip is already there)." else ""
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
 * The row under the points list: the "Detect points" button, and the state of a run. The run continues when the
 * user leaves the tab, so this row shows it at all times.
 */
internal class DetectBar(private val runner: DetectRunner, private val host: DetectHost) : JPanel(BorderLayout(8, 0)) {
    private val button = UiButton("Detect points", Material2AL.INSIGHTS).apply { name = "points-detect" }
    private val status = JLabel().apply {
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
    }
    private val ticker = Timer(1000) { render() }
    private var unlisten: (() -> Unit)? = null

    init {
        name = "points-detect-bar"
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(8, 12, 8, 12),
        )
        add(button, BorderLayout.WEST)
        add(status, BorderLayout.CENTER)
        button.addActionListener { DetectDialog(this, runner, host).open() }
    }

    override fun addNotify() {
        super.addNotify()
        unlisten = runner.listen { EventQueue.invokeLater(::render) }
        render()
    }

    override fun removeNotify() {
        unlisten?.invoke()
        unlisten = null
        ticker.stop()
        super.removeNotify()
    }

    private fun render() {
        val state = runner.state
        status.text = when (state) {
            is DetectState.Running -> DetectTexts.running(state)
            is DetectState.Finished -> "Result ready"
            is DetectState.Failed -> if (state.message == null) "Stopped" else "Failed"
            DetectState.Idle -> ""
        }
        status.toolTipText = status.text.takeIf { it.isNotEmpty() }
        if (state is DetectState.Running) ticker.start() else ticker.stop()
    }
}

/** The dialog of the detection: the settings, the state of the run, Start, Stop, and "Add points". */
internal class DetectDialog(parent: Component, private val runner: DetectRunner, private val host: DetectHost) {
    private val dialog = DialogKit.modal(parent, "Detect points")
    private val saved = runner.loadSettings()

    private val ballPass = SwitchBox("Ball analysis: winners, serve faults, short points (slow)").apply { isSelected = saved.ballPass }
    private val threshold = SegmentedChoice(
        "detect-threshold",
        listOf(
            SegmentedChoice.Option(0.6, "Fewer", "only clear rallies"),
            SegmentedChoice.Option(0.5, "Normal"),
            SegmentedChoice.Option(0.4, "More", "also doubtful ones"),
        ),
    ).apply { selected = listOf(0.6, 0.5, 0.4).minBy { kotlin.math.abs(it - saved.threshold) } }
    private val shortPoints = SwitchBox("Short points from serves (ace, double fault)").apply { isSelected = saved.shortPoints }
    private val padBefore = JTextField(seconds(saved.padBeforeMs)).apply { name = "detect-pad-before" }
    private val padAfter = JTextField(seconds(saved.padAfterMs)).apply { name = "detect-pad-after" }
    private val addWinners = SwitchBox("Set the detected winners").apply { isSelected = saved.addWinners }
    private val minWinner = SegmentedChoice(
        "detect-min-winner",
        listOf(50, 60, 70, 80).map { SegmentedChoice.Option(it, if (it == 50) "All" else "$it %+") },
    ).apply { selected = listOf(50, 60, 70, 80).minBy { kotlin.math.abs(it - saved.minWinnerPercent) } }
    private val addFaults = SwitchBox("Add serve fault clips (advanced scoring mode)").apply { isSelected = saved.addFaults }

    private val status = JLabel(" ").apply {
        font = UiKit.font(12.5f)
        foreground = Palette.FG
    }
    private val detail = DialogKit.errorLine("detect-detail")
    private val start = UiButton("Start detection", Material2MZ.PLAY_ARROW).apply { name = "detect-start" }
    private val stop = UiButton("Stop", kind = UiButton.Kind.DANGER).apply { name = "detect-stop" }
    private val add = UiButton("Add points", kind = UiButton.Kind.LIME).apply { name = "detect-add" }
    private val close = UiButton("Close")
    private val ticker = Timer(1000) { render() }
    private var unlisten: (() -> Unit)? = null

    /** The result file that "Add points" uses: of this run, or the newest one of the project. */
    private var resultFile: File? = null

    fun open() {
        build()
        dialog.setLocationRelativeTo(dialog.owner)
        DialogKit.showModal(dialog)
    }

    /** Builds the packed dialog without showing it. */
    internal fun build(): javax.swing.JDialog {
        val run = DialogGroup("Detection", Material2AL.INSIGHTS).apply {
            wide(ballPass)
            row("Rallies", threshold)
            wide(shortPoints)
        }
        val import = DialogGroup("Add to the project", Material2AL.ADD).apply {
            row("Seconds before", DialogKit.inputBox(padBefore))
            row("Seconds after", DialogKit.inputBox(padAfter))
            wide(addWinners)
            row("Winner confidence", minWinner)
            wide(addFaults)
        }
        val state = Stack(gap = 2).apply {
            add(status)
            add(detail)
        }
        val body = Stack(pad = Insets(8, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 12).apply {
            add(run)
            add(import)
            add(state)
        }
        dialog.contentPane = DialogKit.content(
            DialogKit.WIDE,
            DialogKit.head("Detect points", "lab feature"),
            DialogKit.scrollBody(body),
            DialogKit.footer(left = listOf(stop), right = listOf(close, start, add)),
        )
        start.addActionListener { startRun() }
        stop.addActionListener { runner.stop() }
        add.addActionListener { addPoints() }
        close.addActionListener { dialog.dispose() }
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent?) {
                unlisten?.invoke()
                ticker.stop()
                runner.saveSettings(settings() ?: saved)
            }
        })
        unlisten = runner.listen { EventQueue.invokeLater(::render) }
        render()
        DialogKit.packToScreen(dialog)
        return dialog
    }

    private fun seconds(ms: Int): String = DisplayFormats.decimal(ms / 1000.0, 1)

    /** The settings of the controls, or null when a time does not parse (the dialog then shows the error). */
    private fun settings(): DetectSettings? {
        val before = DisplayFormats.parseDecimal(padBefore.text)
        val after = DisplayFormats.parseDecimal(padAfter.text)
        if (before == null || after == null) {
            detail.text = "Type the seconds as a number, for example 1.5"
            return null
        }
        return DetectSettings(
            ballPass = ballPass.isSelected,
            threshold = threshold.selected ?: 0.5,
            shortPoints = shortPoints.isSelected,
            padBeforeMs = (before * 1000).toInt(),
            padAfterMs = (after * 1000).toInt(),
            addWinners = addWinners.isSelected,
            minWinnerPercent = minWinner.selected ?: 50,
            addFaults = addFaults.isSelected,
        ).normalized()
    }

    private fun startRun() {
        val settings = settings() ?: return
        val dir = host.projectDir
        val video = host.video
        if (dir == null || video == null) {
            detail.text = "The detection needs a project with one video file."
            return
        }
        runner.saveSettings(settings)
        detail.text = " "
        if (!runner.start(dir, dir.name, video, settings)) detail.text = "A detection is already running."
    }

    private fun addPoints() {
        val settings = settings() ?: return
        val file = resultFile ?: return
        runner.saveSettings(settings)
        val plan = host.importResult(file, settings) ?: return
        detail.foreground = Palette.FG_2
        detail.text = DetectTexts.imported(plan)
        runner.reset()
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
                ?: "No result for this project yet. The full detection takes about 0.7 × the length of the video."
        }
        if (state is DetectState.Failed && mine && state.logFile != null) status.toolTipText = state.logFile.absolutePath
        start.isEnabled = !running
        stop.isVisible = running && mine
        add.isEnabled = !running && resultFile != null
        if (running) ticker.start() else ticker.stop()
    }
}
