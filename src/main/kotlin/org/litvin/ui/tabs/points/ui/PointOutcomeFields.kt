package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.tr
import org.litvin.scoring.ClipChoice
import org.litvin.scoring.ClipOutcome
import org.litvin.scoring.ClipOutcomes
import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.PointOutcomeForm
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * The outcome fields of the Edit dialog in advanced mode (B-68, "Corrections"): the kind of the clip, the winner, the
 * ending, and the error kind. The serve number shows read only, because the pack gives it.
 * - The winner and the ending are active only for a point clip. The error kind is active only for an error ending.
 * - The ending labels name the player who made the ending, as the ending buttons do.
 * - An ace goes to the server: the selection of an ace selects the server as the winner.
 */
internal class PointOutcomeFields(private val form: PointOutcomeForm) {
    private data class Choice<T>(val value: T, val label: String) {
        override fun toString(): String = label
    }

    private val kind = JComboBox(
        ClipChoice.entries.map { Choice(it, kindLabel(it)) }.toTypedArray(),
    ).apply {
        name = "points-edit-kind"
        DialogKit.styleCombo(this)
    }
    private val winner = JComboBox(
        arrayOf(Choice(1, form.p1Name), Choice(2, form.p2Name)),
    ).apply {
        name = "points-edit-winner"
        DialogKit.styleCombo(this)
    }
    private val ending = JComboBox<Choice<Ending?>>().apply {
        name = "points-edit-ending"
        DialogKit.styleCombo(this)
    }
    private val errorKind = JComboBox(
        listOf(null, ErrorKind.NET, ErrorKind.LONG, ErrorKind.WIDE).map { Choice(it, errorKindLabel(it)) }.toTypedArray(),
    ).apply {
        name = "points-edit-error-kind"
        DialogKit.styleCombo(this)
    }
    private val serve = JLabel(serveText()).apply {
        name = "points-edit-serve"
        font = UiKit.font(13f)
        foreground = Palette.FG
    }

    init {
        val current = form.current
        kind.selectedIndex = ClipChoice.entries.indexOf(current.kind)
        winner.selectedIndex = if (current.winner == 2) 1 else 0
        fillEndings(current.ending)
        errorKind.selectedIndex = listOf(null, ErrorKind.NET, ErrorKind.LONG, ErrorKind.WIDE).indexOf(current.errorKind)
        kind.addActionListener { updateEnabled() }
        winner.addActionListener { fillEndings(selectedEnding()) }
        ending.addActionListener {
            // An ace goes to the server, as for the ace keys.
            if (selectedEnding() == Ending.ACE && form.server != null && selectedWinner() != form.server) {
                winner.selectedIndex = form.server - 1
            }
            updateEnabled()
        }
        updateEnabled()
    }

    /** The rows of the dialog: the kind with the serve number, the winner with the ending, and the error kind. */
    fun rows(): List<JComponent> = listOf(
        DialogKit.pair(
            DialogKit.field(tr("pointEdit.kind"), kind),
            DialogKit.field(tr("pointEdit.serve"), serve, note = tr("pointEdit.serve.note").takeIf { form.doubleFault }),
        ),
        DialogKit.pair(
            DialogKit.field(tr("pointEdit.winner"), winner),
            DialogKit.field(tr("pointEdit.ending"), ending),
        ),
        DialogKit.pair(DialogKit.field(tr("pointEdit.errorKind"), errorKind), JLabel()),
    )

    /** The outcome that the fields show, with only the fields that its kind uses. */
    fun outcome(): ClipOutcome = ClipOutcomes.normalized(
        ClipOutcome(
            kind = selectedKind(),
            winner = selectedWinner(),
            ending = selectedEnding(),
            errorKind = selectedErrorKind(),
        ),
        form.server,
    )

    /** True when the outcome is different from the outcome when the dialog opened. */
    fun changed(): Boolean = outcome() != ClipOutcomes.normalized(form.current, form.server)

    private fun selectedKind(): ClipChoice = (kind.selectedItem as Choice<*>).value as ClipChoice
    private fun selectedWinner(): Int = (winner.selectedItem as Choice<*>).value as Int
    private fun selectedEnding(): Ending? = (ending.selectedItem as? Choice<*>)?.value as Ending?
    private fun selectedErrorKind(): ErrorKind? = (errorKind.selectedItem as Choice<*>).value as ErrorKind?

    /** The ending choices with the names of the winner and the opponent. */
    private fun fillEndings(selected: Ending?) {
        val won = selectedWinner()
        val names = listOf(form.p1Name, form.p2Name)
        val choices = ENDINGS.map { item ->
            val label = when (item) {
                null -> tr("pointEdit.ending.none")
                Ending.ACE -> tr("points.ending.ace", "name" to names[won - 1])
                Ending.WINNER -> tr("points.ending.winner", "name" to names[won - 1])
                Ending.FORCED_ERROR -> tr("points.ending.forcedError", "name" to names[2 - won])
                else -> tr("points.ending.unforcedError", "name" to names[2 - won])
            }
            Choice(item, label)
        }
        ending.model = DefaultComboBoxModel(choices.toTypedArray())
        ending.selectedIndex = ENDINGS.indexOf(selected).coerceAtLeast(0)
    }

    private fun updateEnabled() {
        val point = selectedKind() == ClipChoice.POINT
        winner.isEnabled = point
        ending.isEnabled = point
        errorKind.isEnabled = point && selectedEnding()?.isError == true
    }

    private fun serveText(): String = when (form.serve) {
        null -> tr("pointEdit.serve.none")
        2 -> tr("points.serve.second")
        else -> tr("points.serve.first")
    }

    private companion object {
        val ENDINGS = listOf(null, Ending.ACE, Ending.WINNER, Ending.FORCED_ERROR, Ending.UNFORCED_ERROR)

        fun kindLabel(kind: ClipChoice): String = when (kind) {
            ClipChoice.POINT -> tr("pointEdit.kind.point")
            ClipChoice.FAULT -> tr("pointEdit.kind.fault")
            ClipChoice.LET -> tr("points.let")
            ClipChoice.NO_POINT -> tr("pointEdit.kind.noPoint")
        }

        fun errorKindLabel(kind: ErrorKind?): String = when (kind) {
            null -> tr("pointEdit.errorKind.none")
            ErrorKind.NET -> tr("pointEdit.errorKind.net")
            ErrorKind.LONG -> tr("pointEdit.errorKind.long")
            ErrorKind.WIDE -> tr("pointEdit.errorKind.wide")
        }
    }
}
