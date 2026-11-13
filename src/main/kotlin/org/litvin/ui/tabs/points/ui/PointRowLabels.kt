package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.tr
import org.litvin.scoring.ClipKind
import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.PackedClip
import org.litvin.scoring.PointDetailV1

/** The place of a row in its pack (B-68, "Packs"). A pack with one clip and a W clip are [SINGLE]. */
internal enum class PackPosition {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST,
    ;

    /** True for a row of a pack with two clips or more. The list shows these rows as a group. */
    val grouped: Boolean get() = this != SINGLE
}

/**
 * What a row of the points list shows about its pack in advanced mode (B-68 E3b).
 * - [number] is the number of the pack or of the W clip. The row shows it only when [showNumber] is true: on the
 *   first row of the pack.
 * - [label] is the serve and the ending, for example "2nd serve · UE net".
 * - [dimmed] is true for a fault clip and a let clip. They do not give the point.
 */
internal data class PointRowInfo(
    val number: Int,
    val showNumber: Boolean,
    val position: PackPosition,
    val label: RowLabel,
    val dimmed: Boolean,
)

/** The serve part of a row label. */
internal enum class RowServe { FIRST, SECOND, LET, NO_POINT }

/** The ending part of a row label, in a short form. */
internal enum class RowEnding { FAULT, DOUBLE_FAULT, ACE, WINNER, FORCED_ERROR, UNFORCED_ERROR }

/**
 * The label of a row in advanced mode (B-68, "Labels"): the serve, then the ending in a short form, then the error
 * kind when the user gave it. For example "1st serve · Fault", "2nd serve · Double fault", or "2nd serve · UE net".
 * A let clip has the label "Let", and a W clip has the label "No point". A point clip with no ending (Q and E) has
 * only its serve.
 */
internal data class RowLabel(
    val serve: RowServe,
    val ending: RowEnding? = null,
    val errorKind: ErrorKind? = null,
) {
    /** The text of the label in the app language. */
    fun text(): String {
        val serveText = when (serve) {
            RowServe.FIRST -> tr("points.row.serve.first")
            RowServe.SECOND -> tr("points.row.serve.second")
            RowServe.LET -> tr("points.row.serve.let")
            RowServe.NO_POINT -> tr("points.row.serve.noPoint")
        }
        val endingText = when (ending) {
            null -> return serveText
            RowEnding.FAULT -> tr("points.row.ending.fault")
            RowEnding.DOUBLE_FAULT -> tr("points.row.ending.doubleFault")
            RowEnding.ACE -> tr("points.row.ending.ace")
            RowEnding.WINNER -> tr("points.row.ending.winner")
            RowEnding.FORCED_ERROR -> tr("points.row.ending.forcedError")
            RowEnding.UNFORCED_ERROR -> tr("points.row.ending.unforcedError")
        }
        val withKind = when (errorKind) {
            null -> endingText
            ErrorKind.NET -> tr("points.row.ending.withKind", "ending" to endingText, "kind" to tr("points.row.errorKind.net"))
            ErrorKind.LONG -> tr("points.row.ending.withKind", "ending" to endingText, "kind" to tr("points.row.errorKind.long"))
            ErrorKind.WIDE -> tr("points.row.ending.withKind", "ending" to endingText, "kind" to tr("points.row.errorKind.wide"))
        }
        return tr("points.row.label", "serve" to serveText, "ending" to withKind)
    }

    companion object {
        /** The label of [clip] with its stored [detail]. */
        fun of(clip: PackedClip, detail: PointDetailV1?): RowLabel {
            val serve = if (clip.serve == 2) RowServe.SECOND else RowServe.FIRST
            return when (clip.kind) {
                ClipKind.NO_POINT -> RowLabel(RowServe.NO_POINT)
                ClipKind.LET -> RowLabel(RowServe.LET)
                ClipKind.FAULT -> RowLabel(serve, RowEnding.FAULT)
                ClipKind.DOUBLE_FAULT -> RowLabel(serve, RowEnding.DOUBLE_FAULT)
                ClipKind.POINT -> {
                    val ending = when (detail?.ending) {
                        Ending.ACE -> RowEnding.ACE
                        Ending.WINNER -> RowEnding.WINNER
                        Ending.FORCED_ERROR -> RowEnding.FORCED_ERROR
                        Ending.UNFORCED_ERROR -> RowEnding.UNFORCED_ERROR
                        Ending.SERVE_FAULT, Ending.LET, null -> null
                    }
                    val kind = detail?.errorKind.takeIf { detail?.ending?.isError == true }
                    RowLabel(serve, ending, kind)
                }
            }
        }
    }
}
