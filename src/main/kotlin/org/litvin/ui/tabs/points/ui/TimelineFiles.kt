package org.litvin.ui.tabs.points.ui

import org.litvin.media.SourceVideo
import java.awt.Rectangle
import java.io.File

/**
 * One video file of a project of several files (B-49), on the timeline from [startMs] to [endMs] in source time.
 * [number] is the place of the file in the play order. It starts at 1.
 */
data class TimelineFile(val number: Int, val name: String, val startMs: Long, val endMs: Long) {
    /** The text on the block of the file, for example "2 GH020042.MP4". */
    val label: String get() = "$number $name"

    companion object {
        /** The files of [source]. A source of one file gives an empty list: the timeline then shows no file blocks. */
        fun of(source: SourceVideo): List<TimelineFile> =
            source.parts.mapIndexed { index, part -> TimelineFile(index + 1, File(part.path).name, part.offsetMs, part.endMs) }
    }
}

/**
 * One part of the block of a file in the VIDEO track. A replay cuts a block into parts. [label] is the shortened name
 * of the file in the widest part, and null in the other parts and in a part that is too narrow for a name.
 */
internal data class TimelineFileBlock(val file: TimelineFile, val bounds: Rectangle, val label: String?)
