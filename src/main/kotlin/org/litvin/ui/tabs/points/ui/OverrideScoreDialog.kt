package org.litvin.ui.tabs.points.ui

import org.litvin.scoring.ScoreOverrideV1
import org.litvin.scoring.ScorePairV1
import org.litvin.scoring.SetGamesV1
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/**
 * The score after a point, as the app computes it without the override of that point.
 * [points] are the engine points (0, 1, 2, 3 for 0, 15, 30, 40; one more for advantage) or tiebreak points.
 */
internal data class ComputedScore(
    val sets: List<SetGamesV1>,
    val games: ScorePairV1,
    val points: ScorePairV1,
)

/** What the Override score dialog edits, and the names and colors of the two sides. */
internal data class OverrideScoreInput(
    val pointNumber: Int,
    val computed: ComputedScore,
    /** The override that the point has now. An empty override means that the point has none. */
    val current: ScoreOverrideV1,
    val p1Name: String,
    val p2Name: String,
    val p1Color: Color,
    val p2Color: Color,
    /** False for a match without sets, for example a match of games only. The dialog then hides the sets. */
    val showSets: Boolean = true,
    /** False for a match without games, for example a single tiebreak. The dialog then hides the games. */
    val showGames: Boolean = true,
    /** The most sets that the match can have. */
    val maxSets: Int = 5,
    /** True when the score with [ScoreOverrideV1] is in a tiebreak: the points are then plain numbers. */
    val tiebreakOf: (ScoreOverrideV1) -> Boolean = { false },
)

/**
 * The modal "Override score" dialog: the sets, the games and the points after a point.
 * Each part starts as "Auto" with the computed value. A change of a value makes its part "Typed".
 * A typed part stays as the user typed it; an auto part follows the points before it.
 * Save returns the override (an empty override removes it); Cancel returns null.
 */
internal class OverrideScoreDialog(owner: Window?, private val input: OverrideScoreInput) :
    JDialog(owner, tr("points.override"), Dialog.ModalityType.APPLICATION_MODAL) {

    companion object {
        private const val WIDTH = 520
        private const val CELL = 52
        private val POINT_LABELS = listOf("0", "15", "30", "40", "Ad")

        /** Shows the dialog and returns the new override, or null after Cancel. */
        fun show(parent: Component, input: OverrideScoreInput): ScoreOverrideV1? {
            val dialog = OverrideScoreDialog(SwingUtilities.getWindowAncestor(parent), input)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    /** One part of the score: the sets, the games or the points. */
    internal inner class Part(val title: String) {
        var typed = false
            private set
        val status = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        private val label = JLabel().apply { font = UiKit.font(11.5f) }
        private val reset = UiButton(tr("override.part.reset"), kind = UiButton.Kind.QUIET, buttonHeight = 22, fontSize = 11.5f).apply {
            toolTipText = tr("override.part.reset.tooltip", "part" to title)
        }
        var onReset: () -> Unit = {}

        init {
            status.add(label)
            status.add(reset)
            reset.addActionListener {
                typed = false
                onReset()
                refresh()
            }
            refresh()
        }

        fun markTyped() {
            if (updating || typed) return
            typed = true
            refresh()
        }

        fun setTyped(value: Boolean) {
            typed = value
            refresh()
        }

        private fun refresh() {
            label.text = if (typed) tr("override.part.typed") else tr("override.part.auto")
            label.foreground = if (typed) Palette.LIME else Palette.FG_3
            label.toolTipText = if (typed) tr("override.part.typed.tooltip", "part" to title)
            else tr("override.part.auto.tooltip", "part" to title)
            reset.isVisible = typed
            status.revalidate()
            status.repaint()
        }
    }

    internal val setsPart = Part(tr("override.part.sets"))
    internal val gamesPart = Part(tr("override.part.games"))
    internal val pointsPart = Part(tr("override.part.points"))

    /** The games of each set: one field for each side. */
    internal val setFields = mutableListOf<Pair<JTextField, JTextField>>()
    internal val gamesFields = numberField("override-games-1") to numberField("override-games-2")
    private val pointsCombos = pointsCombo("override-points-1") to pointsCombo("override-points-2")
    private val pointsFields = numberField("override-points-number-1") to numberField("override-points-number-2")

    /** True while the points are tiebreak points (plain numbers). */
    internal var tiebreakPoints = false
        private set

    private var updating = false
    private val grid = JPanel(GridBagLayout()).apply { isOpaque = false }
    private val addSet = UiButton(tr("override.addSet"), kind = UiButton.Kind.QUIET, buttonHeight = 26, fontSize = 12f).apply {
        name = "override-add-set"
        toolTipText = tr("override.addSet.tooltip")
    }
    private val removeSet = UiButton(tr("override.removeSet"), kind = UiButton.Kind.QUIET, buttonHeight = 26, fontSize = 12f).apply {
        name = "override-remove-set"
        toolTipText = tr("override.removeSet.tooltip")
    }

    // The tiebreak points of the sets that the dialog shows. A set keeps them while its games stay the same.
    private var setTiebreaks = mutableListOf<SetGamesV1>()

    internal var result: ScoreOverrideV1? = null
        private set

    init {
        name = "override-score-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        setsPart.onReset = { showSets(input.computed.sets) }
        gamesPart.onReset = { showPair(gamesFields, input.computed.games) }
        pointsPart.onReset = { refreshPointsMode(); showPoints(input.computed.points) }
        addSet.addActionListener {
            if (setFields.size >= input.maxSets) return@addActionListener
            showSets(currentSets() + SetGamesV1())
            setsPart.setTyped(true)
            refreshPointsMode()
        }
        removeSet.addActionListener {
            if (setFields.isEmpty()) return@addActionListener
            showSets(currentSets().dropLast(1))
            setsPart.setTyped(true)
            refreshPointsMode()
        }
        listOf(gamesFields.first, gamesFields.second).forEach { field ->
            onEdit(field) { gamesPart.markTyped(); refreshPointsMode() }
        }
        listOf(pointsFields.first, pointsFields.second).forEach { field -> onEdit(field) { pointsPart.markTyped() } }
        pointsCombos.first.addActionListener { pointsComboChanged(pointsCombos.first, pointsCombos.second) }
        pointsCombos.second.addActionListener { pointsComboChanged(pointsCombos.second, pointsCombos.first) }

        val current = input.current
        updating = true
        try {
            showSets(current.sets ?: input.computed.sets)
            showPair(gamesFields, current.games ?: input.computed.games)
            setsPart.setTyped(current.sets != null)
            gamesPart.setTyped(current.games != null)
            pointsPart.setTyped(current.points != null)
        } finally {
            updating = false
        }
        refreshPointsMode()
        showPoints(current.points ?: input.computed.points)

        val saveButton = UiButton(tr("common.save"), kind = UiButton.Kind.LIME).apply {
            name = "override-save"
            addActionListener { save() }
        }
        val cancelButton = UiButton(tr("common.cancel")).apply {
            name = "override-cancel"
            addActionListener { dispose() }
        }
        val clearButton = UiButton(tr("override.resetAll"), kind = UiButton.Kind.QUIET).apply {
            name = "override-reset-all"
            toolTipText = tr("override.resetAll.tooltip")
            addActionListener {
                result = ScoreOverrideV1()
                dispose()
            }
        }
        val help = WrapText(
            tr("override.help", "number" to input.pointNumber),
            UiKit.font(12f), Palette.FG_2, 1.5f, WIDTH - DialogKit.PAD_X * 2,
        )
        val body = Stack(pad = Insets(8, DialogKit.PAD_X, 14, DialogKit.PAD_X), gap = 14).apply {
            add(help)
            add(grid)
        }
        contentPane = DialogKit.content(
            WIDTH,
            DialogKit.head(tr("points.override")),
            body,
            DialogKit.footer(left = listOf(clearButton), right = listOf(cancelButton, saveButton)),
        )
        rootPane.defaultButton = saveButton
        DialogKit.onEscape(this) { dispose() }
        DialogKit.packToScreen(this)
    }

    /** The override of the typed parts. */
    internal fun buildOverride(): ScoreOverrideV1 = ScoreOverrideV1(
        sets = if (setsPart.typed && input.showSets) currentSets() else null,
        games = if (gamesPart.typed && input.showGames) readPair(gamesFields) else null,
        points = if (pointsPart.typed) currentPoints() else null,
    )

    private fun save() {
        result = buildOverride()
        dispose()
    }

    /** The sets in the fields. A set keeps its tiebreak points while its games stay the same. */
    private fun currentSets(): List<SetGamesV1> = setFields.mapIndexed { i, (f1, f2) ->
        val p1 = f1.text.toIntOrNull() ?: 0
        val p2 = f2.text.toIntOrNull() ?: 0
        val before = setTiebreaks.getOrNull(i)
        if (before != null && before.p1 == p1 && before.p2 == p2) before else SetGamesV1(p1, p2)
    }

    private fun currentPoints(): ScorePairV1 {
        if (tiebreakPoints) return readPair(pointsFields)
        val p1 = pointsCombos.first.selectedIndex.coerceAtLeast(0)
        val p2 = pointsCombos.second.selectedIndex.coerceAtLeast(0)
        // Advantage: 4–3 in the engine.
        return when {
            p1 == 4 -> ScorePairV1(4, 3)
            p2 == 4 -> ScorePairV1(3, 4)
            else -> ScorePairV1(p1, p2)
        }
    }

    /** The points change between tiebreak numbers and 0, 15, 30, 40, Ad when the sets or the games change. */
    private fun refreshPointsMode() {
        val tiebreak = input.tiebreakOf(
            ScoreOverrideV1(
                sets = if (setsPart.typed) currentSets() else null,
                games = if (gamesPart.typed) readPair(gamesFields) else null,
            ),
        )
        if (tiebreak == tiebreakPoints && grid.componentCount > 0) return
        val points = if (grid.componentCount > 0) currentPoints() else input.computed.points
        tiebreakPoints = tiebreak
        rebuildGrid()
        showPoints(points)
    }

    private fun showPoints(points: ScorePairV1) {
        updating = true
        try {
            if (tiebreakPoints) {
                showPair(pointsFields, points)
            } else {
                // Without a lead of one at 40 or more, the points show as 40–40.
                val (p1, p2) = when {
                    points.p1 >= 3 && points.p2 >= 3 && points.p1 > points.p2 -> 4 to 3
                    points.p1 >= 3 && points.p2 >= 3 && points.p2 > points.p1 -> 3 to 4
                    else -> points.p1.coerceIn(0, 3) to points.p2.coerceIn(0, 3)
                }
                pointsCombos.first.selectedIndex = p1
                pointsCombos.second.selectedIndex = p2
            }
        } finally {
            updating = false
        }
    }

    /** Only one side can have the advantage, and the other side then has 40. */
    private fun pointsComboChanged(changed: JComboBox<String>, other: JComboBox<String>) {
        if (updating) return
        updating = true
        try {
            if (changed.selectedIndex == 4) other.selectedIndex = 3
            else if (other.selectedIndex == 4 && changed.selectedIndex != 3) other.selectedIndex = 3
        } finally {
            updating = false
        }
        pointsPart.markTyped()
    }

    private fun showSets(sets: List<SetGamesV1>) {
        updating = true
        try {
            setTiebreaks = sets.toMutableList()
            setFields.clear()
            sets.forEachIndexed { i, set ->
                val pair = numberField("override-set-${i + 1}-1") to numberField("override-set-${i + 1}-2")
                pair.first.text = set.p1.toString()
                pair.second.text = set.p2.toString()
                listOf(pair.first, pair.second).forEach { field ->
                    onEdit(field) { setsPart.markTyped(); refreshPointsMode() }
                }
                setFields += pair
            }
        } finally {
            updating = false
        }
        rebuildGrid()
    }

    private fun showPair(fields: Pair<JTextField, JTextField>, pair: ScorePairV1) {
        updating = true
        try {
            fields.first.text = pair.p1.toString()
            fields.second.text = pair.p2.toString()
        } finally {
            updating = false
        }
    }

    private fun readPair(fields: Pair<JTextField, JTextField>) =
        ScorePairV1(fields.first.text.toIntOrNull() ?: 0, fields.second.text.toIntOrNull() ?: 0)

    /**
     * The grid: a caption row, a row for each side, and the status row.
     * The columns are the side, the sets (one column for each set, then + Set and − Set), the games, and the points.
     */
    private fun rebuildGrid() {
        grid.removeAll()
        var column = 1
        fun at(x: Int, y: Int, component: JComponent, width: Int = 1, left: Int = 0, fill: Boolean = false) {
            grid.add(component, GridBagConstraints().apply {
                gridx = x; gridy = y; gridwidth = width
                anchor = GridBagConstraints.WEST
                insets = Insets(0, left, 6, 6)
                if (fill) this.fill = GridBagConstraints.HORIZONTAL
            })
        }
        at(0, 2, SideLabel(input.p1Name, input.p1Color))
        at(0, 3, SideLabel(input.p2Name, input.p2Color))
        if (input.showSets) {
            val first = column
            setFields.forEachIndexed { i, (f1, f2) ->
                at(column, 1, caption(tr("override.set", "number" to i + 1), Palette.FG_3))
                at(column, 2, cell(f1))
                at(column, 3, cell(f2))
                column++
            }
            val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(addSet, BorderLayout.NORTH)
                    add(removeSet, BorderLayout.SOUTH)
                })
            }
            addSet.isEnabled = setFields.size < input.maxSets
            removeSet.isEnabled = setFields.isNotEmpty()
            grid.add(buttons, GridBagConstraints().apply {
                gridx = column; gridy = 2; gridheight = 2
                anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 6, 6)
            })
            column++
            at(first, 0, caption(DisplayFormats.upper(tr("override.sets"))), width = column - first)
            at(first, 4, setsPart.status, width = column - first)
        }
        if (input.showGames) {
            at(column, 0, caption(DisplayFormats.upper(tr("override.games"))), left = 12)
            at(column, 2, cell(gamesFields.first), left = 12)
            at(column, 3, cell(gamesFields.second), left = 12)
            at(column, 4, gamesPart.status, left = 12)
            column++
        }
        at(column, 0, caption(DisplayFormats.upper(tr("override.points"))), left = 12)
        if (tiebreakPoints) {
            at(column, 2, cell(pointsFields.first), left = 12)
            at(column, 3, cell(pointsFields.second), left = 12)
        } else {
            at(column, 2, pointsCombos.first, left = 12)
            at(column, 3, pointsCombos.second, left = 12)
        }
        at(column, 4, pointsPart.status, left = 12)
        // The last column takes the free width, so the grid stays at the left.
        grid.add(JPanel().apply { isOpaque = false }, GridBagConstraints().apply {
            gridx = column + 1; gridy = 0; weightx = 1.0
        })
        grid.revalidate()
        grid.repaint()
        if (isDisplayable) pack()
    }

    private fun caption(text: String, color: Color = Palette.FG_2) = JLabel(text).apply {
        font = if (text == text.uppercase()) UiKit.trackedFont(10.5f, 0.07) else UiKit.font(11f)
        foreground = color
    }

    private fun cell(field: JTextField): JComponent = DialogKit.inputBox(field).apply {
        preferredSize = Dimension(CELL, preferredSize.height)
        minimumSize = preferredSize
    }

    private fun numberField(componentName: String) = JTextField(2).apply {
        name = componentName
        horizontalAlignment = JTextField.CENTER
        (document as AbstractDocument).documentFilter = DigitsFilter(2)
    }

    private fun pointsCombo(componentName: String) = JComboBox(POINT_LABELS.toTypedArray()).apply {
        name = componentName
        DialogKit.styleCombo(this)
        preferredSize = Dimension(76, preferredSize.height)
    }

    private fun onEdit(field: JTextField, action: () -> Unit) {
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = edited()
            override fun removeUpdate(e: DocumentEvent) = edited()
            override fun changedUpdate(e: DocumentEvent) = edited()
            private fun edited() {
                if (!updating) action()
            }
        })
    }

    /** The name of a side with its color strip. */
    private class SideLabel(private val text: String, private val color: Color) : JComponent() {
        private val textFont get() = UiKit.font(13f, UiKit.Weight.SEMIBOLD)

        override fun getPreferredSize() = Dimension(130, DialogKit.INPUT_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                g2.color = color
                g2.fillRoundRect(0, 4, 4, height - 8, 2, 2)
                val name = UiKit.ellipsize(text, textFont, width - 14f)
                UiKit.drawText(g2, name, textFont, Palette.FG, 12f, 0f, height.toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    /** Digits only, up to [maxLength] characters. */
    private class DigitsFilter(private val maxLength: Int) : DocumentFilter() {
        override fun insertString(fb: FilterBypass, offset: Int, text: String?, attr: AttributeSet?) =
            replace(fb, offset, 0, text, attr)

        override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
            val digits = text.orEmpty().filter(Char::isDigit)
            val allowed = maxLength - (fb.document.length - length)
            super.replace(fb, offset, length, digits.take(allowed.coerceAtLeast(0)), attrs)
        }
    }
}
