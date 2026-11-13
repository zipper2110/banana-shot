package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.tr
import org.litvin.scoring.ErrorKind
import org.litvin.ui.commons.KeyChipStyle
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

/** What the error kind strip shows (B-68, "Follow-up questions"). */
internal data class ErrorKindStripState(
    /** False hides the strip. */
    val visible: Boolean = false,
    /** True while the strip asks the question. Then the digit keys answer it. */
    val open: Boolean = false,
    /** The current error kind of the clip, or null when the clip has no error kind. */
    val answer: ErrorKind? = null,
    /** The ending of the clip, for example "Ben unforced error". */
    val ending: String = "",
)

/**
 * The error kind strip under the point bar (B-68, "Follow-up questions"). It is a row of the point bar area, not a
 * part of the video, because a Swing component over the native video shows a black rectangle.
 * - It shows the ending of the clip, the current answer, and the answers Net, Long, and Wide with their digit keys.
 * - A click on an answer sets or changes the error kind ([onAnswer]).
 * - A click on the strip opens the question again ([onOpen]).
 * - [keyOf] gives the digit key of each answer.
 * The strip never takes the keyboard focus, so that Space stays the play key.
 */
internal class ErrorKindStrip(
    answers: List<ErrorKind>,
    keyOf: (ErrorKind) -> String,
    /** The keys that close the question (C, R, and Esc), for the hint at the end of the open strip. */
    closeKeys: Triple<String, String, String>,
    onAnswer: (ErrorKind) -> Unit,
    onOpen: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)) {
    private val caption = JLabel().apply {
        name = "points-error-kind-caption"
        font = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
    }
    private val buttons: Map<ErrorKind, ScoringButton> = answers.associateWith { kind ->
        val key = keyOf(kind)
        ScoringButton(label(kind), keys = listOf(key), buttonHeight = BUTTON_HEIGHT, padding = 10, keyStyle = KeyChipStyle.SMALL).apply {
            name = "points-error-kind-${kind.name.lowercase()}"
            toolTipText = tooltip(kind, key)
            addActionListener { onAnswer(kind) }
        }
    }
    private val closeHint = JLabel(
        tr("points.errorKind.closeHint", "start" to closeKeys.first, "next" to closeKeys.second, "cancel" to closeKeys.third),
    ).apply {
        name = "points-error-kind-close-hint"
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
    }

    private var state = ErrorKindStripState()

    init {
        name = "points-error-kind-strip"
        isOpaque = true
        background = Palette.OVERLAY
        toolTipText = tr("points.errorKind.strip.tooltip")
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(caption)
        buttons.values.forEach(::add)
        add(closeHint)
        // A click on the strip, outside the answers, opens the question again.
        val reopen = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!state.open) onOpen()
            }
        }
        addMouseListener(reopen)
        caption.addMouseListener(reopen)
        isVisible = false
        render(state)
    }

    fun render(state: ErrorKindStripState) {
        this.state = state
        caption.text = if (state.open) {
            tr("points.errorKind.question", "ending" to state.ending)
        } else {
            tr("points.errorKind.caption", "ending" to state.ending)
        }
        caption.foreground = if (state.open) Palette.FG else Palette.FG_2
        buttons.forEach { (kind, button) ->
            button.kind = if (kind == state.answer) ScoringButton.Kind.LIME else ScoringButton.Kind.SECONDARY
        }
        closeHint.isVisible = state.open
        // The open question has a lime line at the left: the digit keys answer it now.
        val left = if (state.open) OPEN_LINE else 0
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, left, 0, 0, Palette.LIME_FILL),
                BorderFactory.createEmptyBorder(6, 16 - left, 6, 16),
            ),
        )
        if (isVisible != state.visible) {
            isVisible = state.visible
            parent?.revalidate()
        }
        revalidate()
        repaint()
    }

    private companion object {
        const val BUTTON_HEIGHT = 28
        const val OPEN_LINE = 3

        fun label(kind: ErrorKind): String = when (kind) {
            ErrorKind.NET -> tr("points.errorKind.net")
            ErrorKind.LONG -> tr("points.errorKind.long")
            ErrorKind.WIDE -> tr("points.errorKind.wide")
        }

        fun tooltip(kind: ErrorKind, key: String): String = when (kind) {
            ErrorKind.NET -> tr("points.errorKind.net.tooltip", "key" to key)
            ErrorKind.LONG -> tr("points.errorKind.long.tooltip", "key" to key)
            ErrorKind.WIDE -> tr("points.errorKind.wide.tooltip", "key" to key)
        }
    }
}
