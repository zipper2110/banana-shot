package org.litvin.ui.tabs.points

import org.litvin.i18n.tr
import org.litvin.scoring.EndingChoice
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.tabs.points.ui.EndingButtons
import org.litvin.ui.tabs.points.ui.OutcomeButton
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The second row of the point bar in advanced mode (B-68). It has the ending buttons over the full width of the bar,
 * so that their labels stay readable on a small screen. The left half gives the point to player 1, and the right half
 * gives it to player 2.
 * - The buttons show the full labels ("Ben unforced error") when all of them fit.
 * - Else they show the short labels ("Unforced"). A caption "Point for Ana" then starts each half, because a short
 *   label does not tell who gets the point. When the caption does not fit on one line, it shows on two lines.
 * - While the bar asks "Who serves?", the row shows the question and one button for each player.
 */
internal class EndingsRow(
    private val endings: EndingButtons,
    private val question: JLabel,
    private val answers: List<OutcomeButton>,
) : JPanel(null) {
    private val captions = listOf(1, 2).map { player ->
        JLabel().apply {
            name = "points-endings-group-$player"
            font = UiKit.font(11.5f, UiKit.Weight.SEMIBOLD)
            foreground = Palette.FG_2
        }
    }

    /** The caption texts, without the HTML of the two-line form. */
    private val captionTexts = arrayOf("", "")

    init {
        name = "points-endings-row"
        isOpaque = false
        endings.buttons.values.forEach(::add)
        add(question)
        answers.forEach(::add)
        captions.forEach(::add)
    }

    fun setPlayers(players: ScorePlayers) {
        captions.forEachIndexed { i, _ -> captionTexts[i] = tr("points.endings.group", "name" to players.name(i + 1)) }
        revalidate()
    }

    override fun getPreferredSize() = Dimension(0, HEIGHT)

    override fun getMinimumSize() = Dimension(0, HEIGHT)

    override fun doLayout() {
        if (question.isVisible) {
            captions.forEach { it.isVisible = false }
            layoutQuestion()
            return
        }
        val half = max(0, (width - GROUP_SPLIT) / 2)
        val groups = listOf(1, 2).map(::group)
        val full = groups.map { group -> group.map { (choice, button) -> button.widthFor(endings.fullLabel(choice)) } }
        val short = groups.map { group -> group.map { (choice, button) -> button.widthFor(endings.shortLabel(choice)) } }
        val font = captions[0].font
        val oneLine = captionTexts.map { ceil(UiKit.textWidth(it, font)).toInt() + CAPTION_GAP }
        val twoLines = captionTexts.map { text -> CaptionLines.split(text) { UiKit.textWidth(it, font) } }
        val twoLineWidths = twoLines.map { lines -> ceil(lines.maxOf { UiKit.textWidth(it, font) }).toInt() + CAPTION_GAP }
        fun fits(widths: List<Int>, caption: Int) = caption + widths.sum() + gaps(widths.size) <= half
        val useFull = groups.indices.all { fits(full[it], 0) }
        val useOneLine = !useFull && groups.indices.all { fits(short[it], oneLine[it]) }
        val showCaptions = useOneLine || (!useFull && groups.indices.all { fits(short[it], twoLineWidths[it]) })
        val captionWidths = if (useOneLine) oneLine else twoLineWidths
        captions.forEachIndexed { i, caption ->
            val text = if (useOneLine) captionTexts[i] else twoLines[i].joinToString("<br>", "<html>", "</html>", transform = ::escape)
            if (caption.text != text) caption.text = text
        }
        groups.flatten().forEach { (choice, button) ->
            val label = if (useFull) endings.fullLabel(choice) else endings.shortLabel(choice)
            if (button.text != label) button.text = label
        }
        groups.forEachIndexed { i, group ->
            val start = if (i == 0) 0 else width - half
            var x = start
            captions[i].isVisible = showCaptions
            if (showCaptions) {
                captions[i].setBounds(x, 0, captionWidths[i] - CAPTION_GAP, HEIGHT)
                x += captionWidths[i]
            }
            layoutGroup(group.map { it.second }, if (useFull) full[i] else short[i], x, start + half)
        }
    }

    /** The visible buttons of the group that gives the point to [player], in the order of the point bar. */
    private fun group(player: Int): List<Pair<EndingChoice, OutcomeButton>> =
        EndingButtons.ORDER.filter { it.winner == player }.mapNotNull { choice ->
            endings.buttons[choice]?.takeIf { it.isVisible }?.let { choice to it }
        }

    /**
     * Places [buttons] from [from] to [to]. Each button gets the width of its label and an equal part of the free space.
     * When the labels do not fit, each button gets a part of the width for its label, and the label ends with "…".
     */
    private fun layoutGroup(buttons: List<OutcomeButton>, needs: List<Int>, from: Int, to: Int) {
        if (buttons.isEmpty()) return
        val area = max(0, to - from - gaps(buttons.size))
        val extra = max(0, area - needs.sum()) / buttons.size
        var x = from
        buttons.forEachIndexed { i, button ->
            val share = if (needs.sum() <= area) needs[i] + extra else (needs[i].toLong() * area / needs.sum().coerceAtLeast(1)).toInt()
            val w = if (i == buttons.lastIndex) to - x else share
            button.setBounds(x, 0, max(0, w), HEIGHT)
            x += w + OUTCOME_GAP
        }
    }

    /** "Who serves?" and one button for each player. */
    private fun layoutQuestion() {
        val label = min(ceil(UiKit.textWidth(question.text, question.font)).toInt() + 2, width / 3)
        question.setBounds(0, 0, label, HEIGHT)
        val x = label + LABEL_GAP
        val w = max(0, (width - x - OUTCOME_GAP) / 2)
        answers[0].setBounds(x, 0, w, HEIGHT)
        answers[1].setBounds(x + w + OUTCOME_GAP, 0, max(0, width - x - w - OUTCOME_GAP), HEIGHT)
    }

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun gaps(count: Int): Int = (count - 1).coerceAtLeast(0) * OUTCOME_GAP

    private companion object {
        const val HEIGHT = PointsScoringControls.HEIGHT

        /** The gap between the two halves. */
        const val GROUP_SPLIT = 24
        const val CAPTION_GAP = 10
        const val LABEL_GAP = 8
        const val OUTCOME_GAP = 6
    }
}

/** Splits a caption into two lines (B-68). */
internal object CaptionLines {
    /**
     * The two lines of [text] with the smallest width. The split is at a space when the text has one, else between two
     * characters (for example in Japanese and Chinese). A text of one character stays on one line.
     */
    fun split(text: String, width: (String) -> Float): List<String> {
        val breaks = text.indices.filter { text[it] == ' ' }.ifEmpty { (1 until text.length).toList() }
        val best = breaks.minByOrNull { at -> maxOf(width(text.substring(0, at).trimEnd()), width(text.substring(at).trimStart())) }
            ?: return listOf(text)
        return listOf(text.substring(0, best).trimEnd(), text.substring(best).trimStart())
    }
}
