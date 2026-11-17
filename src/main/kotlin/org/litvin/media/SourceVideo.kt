package org.litvin.media

import java.io.File
import java.util.Locale

/**
 * One file of a source video of several files (B-49). [startMs] is the start time of the file (ffprobe
 * "format=start_time"). [durationMs] is the length of the file (ffprobe "format=duration").
 * The project manifest keeps this list.
 */
data class SourceFile(val path: String, val startMs: Long = 0L, val durationMs: Long = 0L)

/**
 * The video of a project: one video file, or several files that play one after the other as one timeline (B-49).
 *
 * A project of several files keeps the files in an mpv EDL file ([EDL_FILE_NAME] in the project folder), and
 * [path] is the path of that file. mpv plays the EDL file as one video. Each line of the EDL file has a file, its
 * start time and its length. Thus the timeline of mpv and the inputs of ffmpeg ([spans]) use the same times.
 * The start time of each line is the start time of the file, because an input seek of ffmpeg ("-ss") also counts
 * from the start time of the file.
 *
 * All other paths are one video file. Then [spans] and [at] give the same times on that file.
 */
class SourceVideo private constructor(
    /** The path that mpv plays: the video file, or the EDL file. */
    val path: String,
    /** The files of an EDL source, in play order. Empty for one video file. */
    val parts: List<Part>,
) {
    /** One file of the timeline. [offsetMs] is the time on the timeline where the file starts. */
    data class Part(val path: String, val startMs: Long, val durationMs: Long, val offsetMs: Long) {
        val endMs: Long get() = offsetMs + durationMs
    }

    /** One ffmpeg input: from [startMs] in the time of the file [path], for [durationMs], or to the end of the file for null. */
    data class Span(val path: String, val startMs: Long, val durationMs: Long?)

    /** True when the source has several files. */
    val isJoined: Boolean get() = parts.isNotEmpty()

    /** All video files, in play order. */
    val files: List<String> get() = if (isJoined) parts.map { it.path } else listOf(path)

    /** The first video file. The probes of the format (size, frame rate, audio) read it, because all files have the same format. */
    val firstFile: String get() = files.first()

    /** The last video file. */
    val lastFile: String get() = files.last()

    /** The length of the timeline of several files. Null for one video file: a probe of the file gives it. */
    val durationMs: Long? get() = parts.lastOrNull()?.endMs

    /** The video files that are not on the disk. */
    fun missingFiles(): List<String> = files.filterNot { File(it).isFile }

    /** True when all video files are on the disk. */
    fun filesExist(): Boolean = missingFiles().isEmpty()

    /**
     * The ffmpeg inputs for the timeline range [startMs, endMs). A null [endMs] goes to the end of the video.
     * A range that crosses the end of a file gives one input for each file. The first file starts at its start,
     * and the last file goes past its end when the range does, as for one video file.
     */
    fun spans(startMs: Long, endMs: Long?): List<Span> {
        if (!isJoined) return listOf(Span(path, startMs, endMs?.let { (it - startMs).coerceAtLeast(0L) }))
        val result = mutableListOf<Span>()
        parts.forEachIndexed { index, part ->
            val first = index == 0
            val last = index == parts.lastIndex
            val from = if (first) startMs.coerceAtMost(part.endMs) else maxOf(startMs, part.offsetMs)
            if (!last && from >= part.endMs) return@forEachIndexed
            val to = if (last) endMs else minOf(endMs ?: part.endMs, part.endMs)
            if (to != null && to <= from) return@forEachIndexed
            result += Span(part.path, from - part.offsetMs, to?.let { it - from })
        }
        if (result.isEmpty()) {
            val at = at(startMs)
            return listOf(at.copy(durationMs = endMs?.let { (it - startMs).coerceAtLeast(0L) }))
        }
        return result
    }

    /**
     * The timeline range [startMs, endMs), cut at the file ends. Each result range is in one file. For one video file,
     * the result is the range.
     */
    fun splitAtFiles(startMs: Long, endMs: Long): List<Pair<Long, Long>> {
        if (!isJoined) return listOf(startMs to endMs)
        val cuts = parts.drop(1).map { it.offsetMs }.filter { it in (startMs + 1) until endMs }
        val bounds = listOf(startMs) + cuts + endMs
        return bounds.zipWithNext()
    }

    /** The file and the time in that file of the timeline time [timeMs]. */
    fun at(timeMs: Long): Span {
        if (!isJoined) return Span(path, timeMs, null)
        val part = parts.lastOrNull { it.offsetMs <= timeMs } ?: parts.first()
        return Span(part.path, timeMs - part.offsetMs, null)
    }

    companion object {
        /** The name of the EDL file in the project folder of a project with several files. */
        const val EDL_FILE_NAME = "source.edl"
        private const val HEADER = "# mpv EDL v0"

        /**
         * The source video of [path]. An EDL file that this class wrote gives its files. Any other path is one video file.
         * A damaged EDL file gives a source with the EDL path as one file. Then ffmpeg reports that it cannot read it.
         */
        fun of(path: String): SourceVideo {
            if (!isEdlPath(path)) return SourceVideo(path, emptyList())
            val file = File(path)
            if (!file.isFile) return SourceVideo(path, emptyList())
            // The file is small, so each call reads it again. Then a new file list applies at once.
            val text = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { return SourceVideo(path, emptyList()) }
            return parse(path, text) ?: SourceVideo(path, emptyList())
        }

        /** True for the path of an EDL file. */
        fun isEdlPath(path: String): Boolean = path.endsWith(".edl", ignoreCase = true)

        /** Writes the EDL file [edl] for [files] when its text is not already the same. */
        fun write(edl: File, files: List<SourceFile>) {
            val text = edlText(files)
            if (edl.isFile && runCatching { edl.readText(Charsets.UTF_8) }.getOrNull() == text) return
            edl.parentFile?.mkdirs()
            val temp = File(edl.parentFile, edl.name + ".tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(edl)) {
                edl.delete()
                if (!temp.renameTo(edl)) {
                    temp.delete()
                    error("Could not write ${edl.absolutePath}")
                }
            }
        }

        /**
         * The text of an mpv EDL file. Each path has its length in bytes ("%length%path"), so a comma or a
         * semicolon in a path does not end the path.
         */
        fun edlText(files: List<SourceFile>): String = buildString {
            append(HEADER).append('\n')
            files.forEach { file ->
                append('%').append(file.path.toByteArray(Charsets.UTF_8).size).append('%').append(file.path)
                append(",start=").append(seconds(file.startMs))
                append(",length=").append(seconds(file.durationMs))
                append('\n')
            }
        }

        /** Parses an EDL text of [edlText]. Null for another text. */
        internal fun parse(path: String, text: String): SourceVideo? {
            val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != HEADER) return null
            val parts = mutableListOf<Part>()
            var offset = 0L
            for (line in lines.drop(1)) {
                if (line.startsWith("#")) continue
                val file = parseLine(line) ?: return null
                parts += Part(file.path, file.startMs, file.durationMs, offset)
                offset += file.durationMs
            }
            if (parts.isEmpty()) return null
            return SourceVideo(path, parts)
        }

        private fun parseLine(line: String): SourceFile? {
            if (!line.startsWith("%")) return null
            val close = line.indexOf('%', 1).takeIf { it > 1 } ?: return null
            val byteCount = line.substring(1, close).toIntOrNull() ?: return null
            val rest = line.substring(close + 1).toByteArray(Charsets.UTF_8)
            if (rest.size < byteCount) return null
            val filePath = String(rest, 0, byteCount, Charsets.UTF_8)
            val options = String(rest, byteCount, rest.size - byteCount, Charsets.UTF_8)
                .split(',').filter { it.isNotBlank() }
                .mapNotNull { option -> option.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap()
            val start = options["start"]?.let(::millis) ?: 0L
            val length = options["length"]?.let(::millis) ?: return null
            return SourceFile(filePath, start, length)
        }

        private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

        private fun millis(seconds: String): Long? = seconds.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { Math.round(it * 1000) }
    }
}
