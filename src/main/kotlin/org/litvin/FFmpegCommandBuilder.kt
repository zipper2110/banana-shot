package org.litvin

import org.litvin.adjustments.ColorTimeline
import org.litvin.adjustments.CropRect
import org.litvin.adjustments.GeometryPlan
import org.litvin.adjustments.GeometryTimeline
import org.litvin.points.PointV1
import kotlin.math.roundToLong

/**
 * Task 3.7 — FFmpeg command builder (v1)
 *
 * Builds an ffmpeg command line from: source file, EDL keep list (optional),
 * encoder, preset, resolution.
 *
 * Scope (v0.1.0):
 * - H.264 (libx264) MP4 output only.
 * - Applies scale when needed.
 * - Uses the target bitrate of the job when set. Otherwise uses CRF and VBV maxrate/bufsize from ExportPreset.
 * - Idle-trim ON → one input-level seek (-ss/-t) per kept segment, joined with the concat filter,
 *   so decoding costs the kept duration rather than the whole source span.
 */
object FFmpegCommandBuilder {
    data class BuildParams(
        val sourcePath: String,
        val outputPath: String,
        val preset: ExportPreset,
        val outWidth: Int,
        val outHeight: Int,
        val outputFrameRate: String? = null,
        val encoderLabel: String = "H.264 (libx264)",
        val idleTrim: Boolean = true,
        val keeps: List<PointV1> = emptyList(),
        val subtitlesAssPath: String? = null, // when non-null, burn-in subtitles (scoreboard)
        val adjustments: org.litvin.adjustments.AdjustmentsV1? = null, // optional color/geometry adjustments
        // Source frame size for the crop/rotate geometry. When unknown, the output size gives the aspect.
        val sourceWidth: Int? = null,
        val sourceHeight: Int? = null,
        // Black and white luma codes of the source, for the shadows/highlights curve.
        val toneRange: ToneRange = ToneRange.LIMITED,
        // Output time at which this command's first kept segment starts. Non-zero only when the
        // export is split into chunks: the scoreboard ASS is written once on the whole output
        // timeline, so a chunk has to shift its frames into that timeline before the burn-in.
        val outputTimeOffsetMs: Long = 0,
        // True when this command writes an intermediate chunk that a later pass joins by stream copy.
        val chunkOutput: Boolean = false,
        // Target video bitrate in kilobits per second. Null uses the CRF and bitrate cap of the preset.
        val videoBitrateK: Int? = null,
        // When set, the output is one frozen frame of the source with silent audio, in place of the keeps.
        val freezeFrame: FreezeFrame? = null,
        // False when the source has no audio stream. Then each pass writes silent audio, so that all
        // passes have the same streams and the join can copy them.
        val sourceHasAudio: Boolean = true,
        // A dip to black or white at the start and the end of the pass (B-50). Only a pass of kept segments and a
        // replay pass use them.
        val fadeIn: org.litvin.export.PassFade? = null,
        val fadeOut: org.litvin.export.PassFade? = null,
        // When set, the output is one play of a replay part (B-50), in place of the keeps.
        val replay: ReplayRender? = null,
    )

    /**
     * One play of a replay part [startMs, endMs) of the source (B-50). The video gets the [zoom] frame inside the
     * geometry of the Transform tab: the zoom goes in at the start when [zoomIn], and out at the end when [zoomOut].
     * The sound slows down with a lower pitch, or is silent when [muted].
     */
    data class ReplayRender(
        val startMs: Long,
        val endMs: Long,
        val speed: Double,
        val zoom: org.litvin.points.ReplayZoom?,
        val zoomIn: Boolean,
        val zoomOut: Boolean,
        val muted: Boolean,
        /** The frame rate of the source. The zoom counts the frames, and the output keeps this rate. */
        val sourceFrameRate: org.litvin.export.ExportFrameRate?,
        /** The sample rate of the source audio. The lower pitch changes it and then converts back to it. */
        val sampleRate: Int,
    ) {
        /** The output duration of the play. */
        val durationMs: Long get() = Math.round((endMs - startMs).coerceAtLeast(0L) / speed)
    }

    /**
     * A still frame of the source for [durationMs], for example under the statistics card.
     * [atMs] is the source time just after the frame, or null for the end of the source.
     * The pass uses the same filters and encoder settings as the other passes, so the join can copy the streams.
     */
    data class FreezeFrame(val atMs: Long?, val durationMs: Long)

    /** Silent audio for a source without an audio stream. All passes of one export use the same format. */
    private const val SILENT_AUDIO = "anullsrc=r=48000:cl=stereo"

    /** The length of the source window that the freeze reads. The last frame of the window is the still frame. */
    private const val FREEZE_WINDOW_SECS = 0.25

    data class Result(
        val args: List<String>,
        val preview: String,
    )

    private fun colorFilter(adj: org.litvin.adjustments.AdjustmentsV1?, toneRange: ToneRange): String? {
        if (adj == null) return null
        val values = FfmpegColorAdjustmentStrategy.map(adj, toneRange)
        if (!values.hasEqualizerAdjustments && !values.hasToneAdjustments && !values.hasTemperatureAdjustments) return null
        // Use Locale.US formatting to ensure dot decimal
        fun fmt(d: Double): String = java.lang.String.format(java.util.Locale.US, "%.4f", d)
        val filters = mutableListOf<String>()
        if (values.hasEqualizerAdjustments) {
            filters += "eq=contrast=${fmt(values.contrast)}:saturation=${fmt(values.saturation)}"
        }
        if (values.hasToneAdjustments || values.hasTemperatureAdjustments) {
            filters += lutFilter(values)
        }
        return filters.joinToString(",")
    }

    /**
     * Brightness, shadows/highlights and temperature as lookup tables: the luma table is
     * [FfmpegColorAdjustmentStrategy.toneCurve], and the chroma tables are [FfmpegColorAdjustmentStrategy.chromaCurve].
     * Each tone weight is `s*s*(3-2*s)` of a position that clip() keeps in 0..1. round() gives the nearest code;
     * without it, lutyuv truncates, and a very small change moves a code one full step. The expressions are in single quotes,
     * because a comma inside clip() would otherwise end the filter in the filtergraph syntax.
     * An unchanged plane is `val`, because the lutyuv default clips the plane to the legal range.
     */
    internal fun lutFilter(values: FfmpegColorAdjustments): String {
        fun fmt(d: Double): String = java.lang.String.format(java.util.Locale.US, "%.4f", d)
        fun smooth(position: String): String {
            val s = "clip($position,0,1)"
            return "$s*$s*(3-2*$s)"
        }
        fun shifted(shift: Double): String = if (shift == 0.0) "val" else "'round(val+${fmt(shift)})'"
        val range = values.toneRange
        val zone = fmt(FfmpegColorAdjustmentStrategy.toneZone(range))
        val shadowsEnd = fmt(FfmpegColorAdjustmentStrategy.shadowsEnd(range))
        val highlightsStart = fmt(FfmpegColorAdjustmentStrategy.highlightsStart(range))
        val y = if (!values.hasToneAdjustments) "val" else buildString {
            append("'round(val")
            if (values.brightnessLift != 0.0) append("+${fmt(values.brightnessLift)}")
            if (values.shadowsLift != 0.0) append("+${fmt(values.shadowsLift)}*${smooth("($shadowsEnd-val)/$zone")}")
            if (values.highlightsLift != 0.0) append("+${fmt(values.highlightsLift)}*${smooth("(val-$highlightsStart)/$zone")}")
            append(")'")
        }
        return "lutyuv=y=$y:u=${shifted(values.cbShift)}:v=${shifted(values.crShift)}"
    }

    // Rotate (clockwise, same frame size, corners in the fill color) and crop, before the scale filter.
    // A crop that goes past the frame edges gets a larger canvas with the fill color around the frame.
    // With a known source size, the crop uses the same even pixels as the preview shader.
    // Without it, the crop uses fractions of the frame (iw/ih) and the output size gives the aspect.
    private fun geometryFilter(
        adj: org.litvin.adjustments.AdjustmentsV1?,
        knownWidth: Int?,
        knownHeight: Int?,
        outWidth: Int,
        outHeight: Int,
        atMs: Long = 0L,
    ): String? {
        if (adj == null) return null
        val sourceWidth = knownWidth?.takeIf { it > 0 }
        val sourceHeight = knownHeight?.takeIf { it > 0 }
        val plan = GeometryTimeline.planAt(adj, atMs, sourceWidth ?: outWidth, sourceHeight ?: outHeight)
        return planFilter(plan, sourceWidth, sourceHeight, fillColor(adj))
    }

    private fun planFilter(plan: GeometryPlan, sourceWidth: Int?, sourceHeight: Int?, fill: String): String? {
        if (plan.isIdentity) return null
        val (padX, padY) = plan.padding
        val canvas = Canvas.around(padX, padY, sourceWidth, sourceHeight)
        val filters = mutableListOf<String>()
        canvas.filter(if (plan.hasRotation) fmt6(Math.toRadians(plan.rotationDeg)) else null, fill)?.let { filters += it }
        if (plan.hasCrop) {
            filters += if (sourceWidth != null && sourceHeight != null) {
                val (w, h, x, y) = plan.cropPixels(sourceWidth, sourceHeight).toList()
                "crop=$w:$h:${x + canvas.padXPixels}:${y + canvas.padYPixels}"
            } else {
                val c = canvas.of(plan.crop)
                "crop=w=iw*${fmt6(c.width)}:h=ih*${fmt6(c.height)}:x=iw*${fmt6(c.x)}:y=ih*${fmt6(c.y)}"
            }
        }
        return filters.joinToString(",")
    }

    /** The fill color of the areas outside the rotated frame, in the FFmpeg color syntax. */
    private fun fillColor(adj: org.litvin.adjustments.AdjustmentsV1): String {
        val rgb = adj.backgroundRgb and 0xFFFFFF
        return if (rgb == 0) "black" else "0x%06X".format(rgb)
    }

    /**
     * The frame with room on each side for a crop that goes past the frame edges. [width] and [height] are the
     * FFmpeg expressions of the canvas size, and [scaleX] and [scaleY] are the canvas size relative to the frame.
     * With a known source size, the room is [padXPixels] and [padYPixels] even pixels on each side.
     */
    private class Canvas(
        val width: String,
        val height: String,
        val scaleX: Double,
        val scaleY: Double,
        val padXPixels: Int = 0,
        val padYPixels: Int = 0,
    ) {
        private val padded: Boolean get() = width != "iw" || height != "ih"

        /** [crop] as a fraction of the canvas in place of the frame. */
        fun of(crop: CropRect): CropRect = CropRect(
            (crop.x + (scaleX - 1.0) / 2.0) / scaleX,
            (crop.y + (scaleY - 1.0) / 2.0) / scaleY,
            crop.width / scaleX,
            crop.height / scaleY,
        )

        /**
         * Rotates by [angle] (radians, or an "a=" option) into the canvas, or only pads the frame without a rotation.
         * Both keep the frame center at the canvas center and fill the new areas with [fill].
         */
        fun filter(angle: String?, fill: String): String? = when {
            angle != null -> "rotate=$angle:ow=$width:oh=$height:c=$fill"
            padded -> "pad=w=$width:h=$height:x=(ow-iw)/2:y=(oh-ih)/2:color=$fill"
            else -> null
        }

        companion object {
            /** A canvas with [padX] and [padY] (fractions of the frame) on each side. */
            fun around(padX: Double, padY: Double, sourceWidth: Int?, sourceHeight: Int?): Canvas {
                if (sourceWidth != null && sourceHeight != null) {
                    fun evenPixels(fraction: Double, size: Int) =
                        Math.ceil(fraction * size / 2.0 - 1e-6).toInt().coerceAtLeast(0) * 2
                    val x = evenPixels(padX, sourceWidth)
                    val y = evenPixels(padY, sourceHeight)
                    return Canvas(
                        width = if (x == 0) "iw" else "iw+${2 * x}",
                        height = if (y == 0) "ih" else "ih+${2 * y}",
                        scaleX = (sourceWidth + 2.0 * x) / sourceWidth,
                        scaleY = (sourceHeight + 2.0 * y) / sourceHeight,
                        padXPixels = x,
                        padYPixels = y,
                    )
                }
                val scaleX = 1.0 + 2.0 * padX
                val scaleY = 1.0 + 2.0 * padY
                return Canvas(
                    width = if (padX == 0.0) "iw" else "trunc(iw*${fmt6(scaleX)}/2)*2",
                    height = if (padY == 0.0) "ih" else "trunc(ih*${fmt6(scaleY)}/2)*2",
                    scaleX = scaleX,
                    scaleY = scaleY,
                )
            }
        }
    }

    /**
     * The geometry of the change points (B-47) for the source span [startMs, endMs), and the scale to exactly
     * [outWidth] x [outHeight]. In the filter, t = 0 is the source time [timeOffsetMs].
     *
     * Without a blend in the span, the geometry is fixed. In a blend, the angle and the crop change at each frame:
     * rotate takes the angle as an expression of t. The crop size of FFmpeg cannot change during the stream, so a
     * scale with eval=frame makes the crop rectangle [outWidth] x [outHeight] pixels, and a crop of that fixed size
     * cuts it out. The crop filter keeps in_w of the first frame, so x and y use the scaled size directly.
     */
    private fun timedGeometry(
        adj: org.litvin.adjustments.AdjustmentsV1,
        startMs: Long,
        endMs: Long,
        timeOffsetMs: Long,
        knownWidth: Int?,
        knownHeight: Int?,
        outWidth: Int,
        outHeight: Int,
    ): List<String> {
        val sourceWidth = knownWidth?.takeIf { it > 0 }
        val sourceHeight = knownHeight?.takeIf { it > 0 }
        val width = sourceWidth ?: outWidth
        val height = sourceHeight ?: outHeight
        val scale = "scale=$outWidth:$outHeight"
        val blends = GeometryTimeline.blendsIn(adj, width, height, startMs, endMs)
        if (blends.isEmpty()) {
            val plan = GeometryTimeline.planAt(adj, startMs, width, height)
            return listOfNotNull(planFilter(plan, sourceWidth, sourceHeight, fillColor(adj)), scale, "setsar=1")
        }
        val time = "(t+${(timeOffsetMs / 1000.0).formatSecs()})"
        // The plans in time order: the plan before the first blend, then the end plan of each blend.
        // The angles are unwrapped, so that each blend takes the shorter direction.
        val framePlans = listOf(blends.first().from) + blends.map { it.to }
        // One canvas for all plans, with room for each crop that goes past the frame edges.
        val canvas = Canvas.around(
            framePlans.maxOf { it.padding.first },
            framePlans.maxOf { it.padding.second },
            sourceWidth,
            sourceHeight,
        )
        val plans = framePlans.map { GeometryPlan(it.rotationDeg, canvas.of(it.crop)) }
        val angles = mutableListOf(plans.first().rotationDeg)
        blends.forEach { blend -> angles += angles.last() + GeometryTimeline.rotationDelta(blend.from, blend.to) }

        fun piecewise(values: List<Double>): String {
            var expression = fmt6(values.last())
            for (index in blends.indices.reversed()) {
                val blend = blends[index]
                val from = values[index]
                val to = values[index + 1]
                val start = (blend.startMs / 1000.0).formatSecs()
                val end = (blend.endMs / 1000.0).formatSecs()
                val length = ((blend.endMs - blend.startMs) / 1000.0).formatSecs()
                val s = "clip(($time-$start)/$length,0,1)"
                val mixed = "${fmt6(from)}+${fmt6(to - from)}*$s*$s*(3-2*$s)"
                expression = "if(lt($time,$start),${fmt6(from)},if(lt($time,$end),$mixed,$expression))"
            }
            return expression
        }

        val filters = mutableListOf<String>()
        val angle = if (angles.any { kotlin.math.abs(it) >= 0.001 }) "a='(${piecewise(angles)})*PI/180'" else null
        canvas.filter(angle, fillColor(adj))?.let { filters += it }
        val cropWidth = piecewise(plans.map { it.crop.width })
        val cropHeight = piecewise(plans.map { it.crop.height })
        val scaledWidth = "ceil($outWidth/($cropWidth)/2)*2"
        val scaledHeight = "ceil($outHeight/($cropHeight)/2)*2"
        filters += "scale=w='$scaledWidth':h='$scaledHeight':eval=frame"
        val x = "min((${piecewise(plans.map { it.crop.x })})*$scaledWidth,$scaledWidth-$outWidth)"
        val y = "min((${piecewise(plans.map { it.crop.y })})*$scaledHeight,$scaledHeight-$outHeight)"
        filters += "crop=w=$outWidth:h=$outHeight:x='$x':y='$y'"
        filters += "setsar=1"
        return filters
    }

    private fun fmt6(d: Double): String = java.lang.String.format(java.util.Locale.US, "%.6f", d)

    /** The frame rate that the zoom of a replay counts with when the source rate is not known. */
    private const val FALLBACK_FPS = 30.0

    /**
     * The zoom of a replay (B-50) as a perspective filter, or null without a zoom. The filter maps a part of the
     * input frame to the full frame. The part changes from the full frame to the zoom frame in the first
     * [org.litvin.points.Replays.ZOOM_MS] of the play, and back in the last ones (decision 7 and 8). The expressions
     * count the input frames, because perspective has no time variable.
     */
    internal fun replayZoomFilter(replay: ReplayRender): String? {
        val zoom = replay.zoom ?: return null
        val fps = replay.sourceFrameRate?.framesPerSecond?.takeIf { it > 0.0 } ?: FALLBACK_FPS
        // The source frames in the zoom time. The play is slower, so the zoom takes fewer source frames.
        val zoomFrames = fmt6((fps * org.litvin.points.Replays.ZOOM_MS / 1000.0 * replay.speed).coerceAtLeast(1.0))
        val lastFrame = fmt6(((replay.endMs - replay.startMs) / 1000.0 * fps - 1.0).coerceAtLeast(0.0))
        fun smooth(u: String) = "($u)*($u)*(3-2*($u))"
        val parts = buildList {
            if (replay.zoomIn) add(smooth("clip(in/$zoomFrames,0,1)"))
            if (replay.zoomOut) add(smooth("clip(($lastFrame-in)/$zoomFrames,0,1)"))
        }
        val progress = when (parts.size) {
            0 -> "1"
            1 -> parts.single()
            else -> "min(${parts[0]},${parts[1]})"
        }
        val size = "(1-${fmt6(1.0 - zoom.width)}*$progress)"
        val left = "${fmt6(zoom.x)}*$progress"
        val top = "${fmt6(zoom.y)}*$progress"
        val x0 = "W*($left)"
        val y0 = "H*($top)"
        val x1 = "W*($left+$size)"
        val y1 = "H*($top+$size)"
        val corners = listOf("x0" to x0, "y0" to y0, "x1" to x1, "y1" to y0, "x2" to x0, "y2" to y1, "x3" to x1, "y3" to y1)
        val eval = if (parts.isEmpty()) "init" else "frame"
        return "perspective=" + corners.joinToString(":") { (name, value) -> "$name='$value'" } + ":sense=source:eval=$eval"
    }

    /** The video fades of a pass of [durationSecs]: a dip from black or white at the start, and to it at the end. */
    internal fun fadeFilters(fadeIn: org.litvin.export.PassFade?, fadeOut: org.litvin.export.PassFade?, durationSecs: Double): List<String> {
        fun color(fade: org.litvin.export.PassFade) = if (fade.white) "white" else "black"
        return listOfNotNull(
            fadeIn?.let { "fade=t=in:st=0:d=${(it.durationMs / 1000.0).formatSecs()}:color=${color(it)}" },
            fadeOut?.let {
                val length = (it.durationMs / 1000.0).coerceAtMost(durationSecs)
                "fade=t=out:st=${(durationSecs - length).coerceAtLeast(0.0).formatSecs()}:d=${length.formatSecs()}:color=${color(it)}"
            },
        )
    }

    /** The sound fades that go with [fadeFilters]. */
    internal fun audioFadeFilters(fadeIn: org.litvin.export.PassFade?, fadeOut: org.litvin.export.PassFade?, durationSecs: Double): List<String> =
        listOfNotNull(
            fadeIn?.let { "afade=t=in:st=0:d=${(it.durationMs / 1000.0).formatSecs()}" },
            fadeOut?.let {
                val length = (it.durationMs / 1000.0).coerceAtMost(durationSecs)
                "afade=t=out:st=${(durationSecs - length).coerceAtLeast(0.0).formatSecs()}:d=${length.formatSecs()}"
            },
        )

    /** The output path that sends the images to the standard output as BMP files, one after the other. */
    const val PIPE = "-"

    private val BMP_PIPE_OUTPUT = listOf("-f", "image2pipe", "-c:v", "bmp", PIPE)

    /**
     * The arguments that write one frame of the source to an image file, for example a PNG.
     * The frame gets the crop, rotation and color adjustments of the export, and the width [width].
     * [atMs] is the source time just after the frame, as in [FreezeFrame.atMs].
     * With [outputPath] [PIPE], ffmpeg sends the frame to the standard output as a BMP image. This is faster than a PNG file.
     */
    fun stillFrameArgs(
        sourcePath: String,
        atMs: Long,
        outputPath: String,
        width: Int,
        adjustments: org.litvin.adjustments.AdjustmentsV1? = null,
        sourceWidth: Int? = null,
        sourceHeight: Int? = null,
        toneRange: ToneRange = ToneRange.LIMITED,
    ): List<String> {
        val height = width * 9 / 16
        val filters = listOfNotNull(
            geometryFilter(adjustments, sourceWidth, sourceHeight, width, height, atMs - STILL_FRAME_OFFSET_MS),
            "scale=$width:-2",
            colorFilter(adjustments?.let { ColorTimeline.at(it, atMs - STILL_FRAME_OFFSET_MS) }, toneRange),
        )
        val seekSecs = ((atMs - STILL_FRAME_OFFSET_MS).coerceAtLeast(0) / 1000.0).formatSecs()
        val output = if (outputPath == PIPE) BMP_PIPE_OUTPUT else listOf("-update", "1", outputPath)
        return listOf(
            "-y", "-v", "error", "-ss", seekSecs, "-i", sourcePath,
            "-frames:v", "1", "-vf", filters.joinToString(","),
        ) + output
    }

    /**
     * The arguments that send the key frames of the source from [startMs] to [endMs] to the standard output as BMP images.
     * ffmpeg decodes only the key frames, so this is fast also for a 4K video: one key frame for each second for most cameras.
     * The "showinfo" filter writes the time of each frame to the error output (see [keyFrameTimeMs]).
     * The frames get the adjustments of the middle of the range.
     */
    fun keyFramesArgs(
        sourcePath: String,
        startMs: Long,
        endMs: Long,
        width: Int,
        adjustments: org.litvin.adjustments.AdjustmentsV1? = null,
        sourceWidth: Int? = null,
        sourceHeight: Int? = null,
        toneRange: ToneRange = ToneRange.LIMITED,
    ): List<String> {
        val height = width * 9 / 16
        val middleMs = (startMs + endMs) / 2
        val filters = listOfNotNull(
            geometryFilter(adjustments, sourceWidth, sourceHeight, width, height, middleMs),
            "scale=$width:-2",
            colorFilter(adjustments?.let { ColorTimeline.at(it, middleMs) }, toneRange),
            "showinfo",
        )
        val start = startMs.coerceAtLeast(0)
        return listOf(
            "-hide_banner", "-nostats", "-v", "info", "-skip_frame", "nokey",
            "-ss", (start / 1000.0).formatSecs(), "-t", ((endMs - start).coerceAtLeast(1) / 1000.0).formatSecs(), "-i", sourcePath,
            "-vf", filters.joinToString(","), "-fps_mode", "passthrough",
        ) + BMP_PIPE_OUTPUT
    }

    private val SHOWINFO_TIME = Regex("""pts_time:(-?[0-9.]+)""")

    /** The time of a frame in a "showinfo" line of [keyFramesArgs], from the start of the range. Null for other lines. */
    fun keyFrameTimeMs(line: String): Long? =
        SHOWINFO_TIME.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).roundToLong() }

    /** The still frame is this time before [FreezeFrame.atMs], so it is the last frame of the point. */
    private const val STILL_FRAME_OFFSET_MS = 40L

    fun build(params: BuildParams): Result {
        val p = params
        val v = ExportQualityProfiles.resolve(
            preset = p.preset,
            outWidth = p.outWidth,
            outHeight = p.outHeight,
            outputFrameRate = p.outputFrameRate,
        ).video
        val a = p.preset.audio
        val c = p.preset.container

        // Select video codec based on encoder label; default to libx264
        val isNvenc = p.encoderLabel.contains("NVENC", ignoreCase = true)
        val isQsv = p.encoderLabel.contains("QSV", ignoreCase = true)
        val isAmf = p.encoderLabel.contains("AMF", ignoreCase = true)
        val videoCodec = when {
            isNvenc -> "h264_nvenc"
            isQsv -> "h264_qsv"
            isAmf -> "h264_amf"
            p.encoderLabel.contains("libx264", ignoreCase = true) -> "libx264"
            else -> (v.codec ?: "libx264")
        }

        val args = mutableListOf<String>()
        // Enable info-level logs and show banner; also request machine-readable progress to stderr
        args += listOf("-y", "-v", "info", "-progress", "pipe:2", "-nostats")

        // Idle-trim opens the source once per kept segment with an input-level seek. `-ss` before
        // `-i` seeks through the index and decodes only from the preceding keyframe, so the work is
        // proportional to the kept duration. Cutting with the trim filter instead would decode the
        // whole span up to the last keep and throw most of it away, which made a 5-second export of
        // a one-hour source cost the same as exporting the hour. `-ss` before the input has been
        // frame-accurate since ffmpeg 2.1 (it decodes and discards up to the exact point), so the
        // segment durations still match what ScoreboardTimelineBuilder assumes.
        val freeze = p.freezeFrame
        val replay = p.replay?.takeIf { freeze == null }
        val segmentInputs = freeze == null && replay == null && p.idleTrim && p.keeps.isNotEmpty()
        if (replay != null) {
            val lengthSecs = (replay.endMs - replay.startMs).coerceAtLeast(0L) / 1000.0
            args += listOf("-ss", (replay.startMs / 1000.0).formatSecs(), "-t", lengthSecs.formatSecs())
            args += listOf("-i", p.sourcePath)
        } else if (freeze != null) {
            val atMs = freeze.atMs
            if (atMs == null) {
                args += listOf("-sseof", (-FREEZE_WINDOW_SECS).formatSecs())
            } else {
                val startSecs = ((atMs / 1000.0) - FREEZE_WINDOW_SECS).coerceAtLeast(0.0)
                args += listOf("-ss", startSecs.formatSecs(), "-t", FREEZE_WINDOW_SECS.formatSecs())
            }
            args += listOf("-i", p.sourcePath)
        } else if (segmentInputs) {
            p.keeps.forEach { keep ->
                val startSecs = keep.startMs / 1000.0
                val durationSecs = (keep.endMs - keep.startMs).coerceAtLeast(0) / 1000.0
                args += listOf("-ss", startSecs.formatSecs(), "-t", durationSecs.formatSecs())
                args += listOf("-i", p.sourcePath)
            }
        } else {
            args += listOf("-i", p.sourcePath)
            if (!p.sourceHasAudio) {
                // The second input gives the audio. -shortest stops it at the end of the video.
                args += listOf("-f", "lavfi", "-i", SILENT_AUDIO)
            }
        }

        var filterComplex: String? = null
        var vMap = "[vout]"
        var aMap = "[aout]"

        fun buildColorFilter(adj: org.litvin.adjustments.AdjustmentsV1?): String? = colorFilter(adj, p.toneRange)
        // A freeze shows the geometry of its frame. Without change points, the time has no effect.
        // A replay shows the geometry and the colors of its start.
        val geometryAtMs = freeze?.let { it.atMs?.minus(STILL_FRAME_OFFSET_MS) ?: Long.MAX_VALUE } ?: replay?.startMs ?: 0L
        fun buildGeometryFilter(adj: org.litvin.adjustments.AdjustmentsV1?): String? =
            geometryFilter(adj, p.sourceWidth, p.sourceHeight, p.outWidth, p.outHeight, geometryAtMs)

        // With change points (B-47), each part of the video gets the geometry and the colors of its time,
        // and an exact output size, before the concat.
        val timedAdjustments = p.adjustments
            ?.takeIf { freeze == null && (GeometryTimeline.hasKeys(it) || ColorTimeline.hasKeys(it)) }
        val exactOutHeight = if (p.outHeight > 0 && p.outHeight % 2 == 0) p.outHeight else p.outWidth * 9 / 16 / 2 * 2
        fun timedFilters(adj: org.litvin.adjustments.AdjustmentsV1, startMs: Long, endMs: Long): List<String> =
            timedGeometry(adj, startMs, endMs, startMs, p.sourceWidth, p.sourceHeight, p.outWidth, exactOutHeight)

        // A color fade reads its part of the source from one more input, without audio. These inputs come
        // after the other inputs.
        val baseInputCount = when {
            freeze != null || replay != null -> 1
            segmentInputs -> p.keeps.size
            p.sourceHasAudio -> 1
            else -> 2
        }
        val extraInputs = mutableListOf<String>()
        fun addVideoInput(startMs: Long, endMs: Long): Int {
            val index = baseInputCount + extraInputs.count { it == "-i" }
            extraInputs += listOf("-an", "-ss", (startMs / 1000.0).formatSecs())
            if (endMs != Long.MAX_VALUE) extraInputs += listOf("-t", ((endMs - startMs) / 1000.0).formatSecs())
            extraInputs += listOf("-i", p.sourcePath)
            return index
        }

        /**
         * The video of the source span [startMs, endMs) from input [spanInput], as the label [label].
         * A hold has fixed colors. A fade splits its part into the old and the new colors and fades between them.
         */
        fun timedSpan(
            adj: org.litvin.adjustments.AdjustmentsV1,
            startMs: Long,
            endMs: Long,
            spanInput: Int,
            label: String,
        ): List<String> {
            fun chain(piece: ColorTimeline.Piece, input: Int, out: String): List<String> {
                val filters = listOf("setpts=PTS-STARTPTS") + timedFilters(adj, piece.startMs, piece.endMs)
                val base = "[$input:v]" + filters.joinToString(",")
                return when (piece) {
                    is ColorTimeline.Piece.Hold ->
                        listOf(base + buildColorFilter(piece.colors)?.let { ",$it" }.orEmpty() + "[$out]")
                    is ColorTimeline.Piece.Fade -> {
                        val seconds = ((piece.endMs - piece.startMs) / 1000.0).formatSecs()
                        listOf(
                            "$base,split[${out}a][${out}b]",
                            "[${out}a]${buildColorFilter(piece.from) ?: "null"}[${out}from]",
                            "[${out}b]${buildColorFilter(piece.to) ?: "null"}[${out}to]",
                            "[${out}from][${out}to]xfade=transition=fade:duration=$seconds:offset=0[$out]",
                        )
                    }
                }
            }
            val pieces = ColorTimeline.pieces(adj, startMs, endMs)
            if (pieces.size == 1) return chain(pieces.single(), spanInput, label)
            // Each piece reads its own part of the source. The span input still gives the audio.
            val parts = mutableListOf<String>()
            pieces.forEachIndexed { index, piece ->
                parts += chain(piece, addVideoInput(piece.startMs, piece.endMs), "${label}p$index")
            }
            parts += pieces.indices.joinToString("") { "[${label}p$it]" } + "concat=n=${pieces.size}:v=1:a=0[$label]"
            return parts
        }

        val geometry = buildGeometryFilter(p.adjustments)
        // After a crop, "-2" keeps the aspect of the crop rectangle. The crop snaps to even source pixels,
        // so that height can be some pixels different from the output height. Thus scale to the exact
        // output size after a crop. The crop has the frame aspect, so the aspect changes very little.
        val scale = if (geometry != null && p.outHeight > 0 && p.outHeight % 2 == 0) {
            "scale=${p.outWidth}:${p.outHeight}"
        } else {
            "scale=${p.outWidth}:-2"
        }

        if (freeze != null) {
            // Repeat the last frame of the window (tpad), then cut the window away, so that only copies of the
            // last frame stay. This works for any frame rate that has at least one frame in the window.
            val window = FREEZE_WINDOW_SECS.formatSecs()
            val duration = (freeze.durationMs / 1000.0).formatSecs()
            val padding = (freeze.durationMs / 1000.0 + FREEZE_WINDOW_SECS * 2).formatSecs()
            val video = mutableListOf(
                "setpts=PTS-STARTPTS",
                "tpad=stop_mode=clone:stop_duration=$padding",
                "trim=start=$window",
                "setpts=PTS-STARTPTS",
                "trim=duration=$duration",
            )
            geometry?.let { video += it }
            video += scale
            buildColorFilter(p.adjustments?.let { ColorTimeline.at(it, geometryAtMs) })?.let { video += it }
            p.subtitlesAssPath?.let { video += "subtitles='${escapeForFilterPath(it)}'" }
            // The source audio keeps its sample rate and channels, so the AAC settings match the other passes.
            val audio = if (p.sourceHasAudio) {
                "[0:a]asetpts=PTS-STARTPTS,volume=0,apad,atrim=duration=$duration"
            } else {
                "$SILENT_AUDIO,atrim=duration=$duration"
            }
            filterComplex = "[0:v]${video.joinToString(",")}$vMap;$audio$aMap"
        } else if (replay != null) {
            // The zoom works on the frame after the Transform geometry, at the source resolution when it can.
            // The speed and the frame rate come after it, because the zoom counts the source frames.
            val durationSecs = replay.durationMs / 1000.0
            val duration = durationSecs.formatSecs()
            val zoom = replayZoomFilter(replay)
            val video = mutableListOf("setpts=PTS-STARTPTS")
            if (timedAdjustments != null) {
                video += timedFilters(timedAdjustments, replay.startMs, replay.endMs)
                zoom?.let { video += it }
                buildColorFilter(ColorTimeline.at(timedAdjustments, replay.startMs))?.let { video += it }
            } else {
                geometry?.let { video += it }
                zoom?.let { video += it }
                video += scale
                buildColorFilter(p.adjustments)?.let { video += it }
            }
            video += "setpts=PTS/${fmt6(replay.speed)}"
            replay.sourceFrameRate?.let { video += "fps=${it.ffmpegArgument}" }
            p.subtitlesAssPath?.let { video += "subtitles='${escapeForFilterPath(it)}'" }
            video += fadeFilters(p.fadeIn, p.fadeOut, durationSecs)
            val audioFades = audioFadeFilters(p.fadeIn, p.fadeOut, durationSecs)
            val audio = when {
                !p.sourceHasAudio -> "$SILENT_AUDIO,atrim=duration=$duration"
                replay.muted -> "[0:a]asetpts=PTS-STARTPTS,volume=0,apad,atrim=duration=$duration"
                else -> {
                    // A lower sample rate plays the samples slower, so the pitch goes down with the speed (decision 4).
                    // The resample keeps the sample rate of the other passes, so the join can copy the streams.
                    val slowRate = Math.round(replay.sampleRate * replay.speed).coerceAtLeast(1L)
                    (listOf("[0:a]asetpts=PTS-STARTPTS", "asetrate=$slowRate", "aresample=${replay.sampleRate}", "apad", "atrim=duration=$duration") + audioFades)
                        .joinToString(",")
                }
            }
            filterComplex = "[0:v]${video.joinToString(",")}$vMap;$audio$aMap"
        } else if (segmentInputs) {
            // Build the concat graph over the per-segment inputs. Each input was already seeked and
            // length-limited on the command line, so the segment only needs its timestamps rebased.
            val parts = mutableListOf<String>()
            val vLabels = mutableListOf<String>()
            val aLabels = mutableListOf<String>()
            p.keeps.forEachIndexed { idx, keep ->
                val vLbl = "v$idx"
                val aLbl = "a$idx"
                parts += if (timedAdjustments != null) {
                    timedSpan(timedAdjustments, keep.startMs.toLong(), keep.endMs.toLong(), idx, vLbl)
                } else {
                    listOf("[$idx:v]setpts=PTS-STARTPTS[$vLbl]")
                }
                parts += if (p.sourceHasAudio) {
                    "[$idx:a]asetpts=PTS-STARTPTS[$aLbl]"
                } else {
                    val durationSecs = (keep.endMs - keep.startMs).coerceAtLeast(0) / 1000.0
                    "$SILENT_AUDIO,atrim=duration=${durationSecs.formatSecs()}[$aLbl]"
                }
                vLabels += "[$vLbl]"
                aLabels += "[$aLbl]"
            }
            // Concat and scale after concat
            parts += vLabels.joinToString(separator = "") + "concat=n=" + p.keeps.size + ":v=1:a=0[vcat]"
            // Output scaled video into an intermediate label; we may append subtitles next
            parts += if (timedAdjustments != null) {
                // Each segment already has its geometry and the output size.
                "[vcat]null[vsc]"
            } else {
                "[vcat]" + listOfNotNull(geometry, scale).joinToString(",") + "[vsc]"
            }
            // A chunk next to a replay dips to black or white (B-50).
            val chunkSecs = p.keeps.sumOf { (it.endMs - it.startMs).coerceAtLeast(0).toLong() } / 1000.0
            val videoFades = fadeFilters(p.fadeIn, p.fadeOut, chunkSecs)
            val audioFades = audioFadeFilters(p.fadeIn, p.fadeOut, chunkSecs)
            val finalVideo = vMap
            if (videoFades.isNotEmpty()) vMap = "[vfd]"
            if (audioFades.isEmpty()) {
                parts += aLabels.joinToString(separator = "") + "concat=n=${p.keeps.size}:v=0:a=1" + aMap
            } else {
                parts += aLabels.joinToString(separator = "") + "concat=n=${p.keeps.size}:v=0:a=1[acat]"
                parts += "[acat]${audioFades.joinToString(",")}$aMap"
            }
            // Apply color adjustments after scale to match preview, before subtitles
            val color = if (timedAdjustments != null) null else buildColorFilter(p.adjustments)
            var baseLabel = "[vsc]"
            if (color != null) {
                parts += "$baseLabel$color[vclr]"
                baseLabel = "[vclr]"
            }
            // If subtitles present, append burn-in after color; else map baseLabel directly
            val subPath = p.subtitlesAssPath
            if (subPath != null) {
                val esc = escapeForFilterPath(subPath)
                // The subtitles filter picks events by frame timestamp. A chunk's frames start at
                // zero, so shift them onto the whole-output timeline for the burn-in and rebase
                // afterwards; the chunk still encodes from zero.
                val offsetSecs = p.outputTimeOffsetMs / 1000.0
                val burnIn = if (p.outputTimeOffsetMs > 0) {
                    "setpts=PTS+${offsetSecs.formatSecs()}/TB,subtitles='${esc}',setpts=PTS-STARTPTS"
                } else {
                    "subtitles='${esc}'"
                }
                parts += "$baseLabel" + burnIn + vMap
            } else {
                vMap = baseLabel
            }
            if (videoFades.isNotEmpty()) {
                parts += "$vMap${videoFades.joinToString(",")}$finalVideo"
                vMap = finalVideo
            }
            filterComplex = parts.joinToString(";")
        } else if (timedAdjustments != null) {
            // The whole video with change points: the first input gives the audio, and the pieces the video.
            val parts = timedSpan(timedAdjustments, 0L, Long.MAX_VALUE, 0, "vspan").toMutableList()
            val subPath = p.subtitlesAssPath
            if (subPath != null) {
                parts += "[vspan]subtitles='${escapeForFilterPath(subPath)}'$vMap"
            } else {
                vMap = "[vspan]"
            }
            aMap = if (p.sourceHasAudio) "0:a:0" else "1:a:0"
            filterComplex = parts.joinToString(";")
        }
        args += extraInputs

        // Video options
        args += listOf("-c:v", videoCodec)
        val targetBitrateK = p.videoBitrateK?.takeIf { it > 0 }
        if (isNvenc) {
            if (targetBitrateK == null) {
                // Map CRF to CQ for NVENC
                val cq = (v.crf ?: 21).coerceIn(0, 51)
                args += listOf("-cq", cq.toString())
            } else {
                args += listOf("-rc", "vbr")
            }
            // NVENC presets: default to a medium level if not specified
            val nvPreset = when (v.x264Preset?.lowercase()) {
                "veryfast" -> "p1"
                "faster", "fast" -> "p3"
                "medium", null -> "p5"
                "slow" -> "p6"
                else -> "p5"
            }
            args += listOf("-preset", nvPreset)
            // Use high profile yuv420p unless overridden
            args += listOf("-profile:v", "high")
        } else if (p.encoderLabel.contains("libx264", ignoreCase = true)) {
            // Software x264 tuning
            v.x264Preset?.let { args += listOf("-preset", it) }
            if (targetBitrateK == null) v.crf?.let { args += listOf("-crf", it.toString()) }
        } else {
            // Other hardware encoders (QSV/AMF): keep defaults; consider mapping CRF to a vendor-specific quality if needed in future.
        }
        if (targetBitrateK != null) {
            // Average bitrate with some room for fast motion, so the file size stays predictable.
            args += listOf("-b:v", "${targetBitrateK}k")
            args += listOf("-maxrate", "${targetBitrateK * 3 / 2}k", "-bufsize", "${targetBitrateK * 2}k")
        } else {
            v.vbvMaxrateK?.let { maxk ->
                args += listOf("-maxrate", "${maxk}k")
                v.vbvBufsizeK?.let { bufk -> args += listOf("-bufsize", "${bufk}k") }
            }
        }
        v.pixelFormat?.let { args += listOf("-pix_fmt", it) }
        p.outputFrameRate?.let { args += listOf("-r", it) }
        // GOP length optional — without knowing FPS, skip to avoid incorrect value in MVP

        // Audio options
        if (a != null) {
            val acodec = a.codec ?: "aac"
            args += listOf("-c:a", acodec)
            a.bitrateK?.let { args += listOf("-b:a", "${it}k") }
            a.channels?.let { args += listOf("-ac", it.toString()) }
        } else {
            // Default to AAC stereo
            args += listOf("-c:a", "aac", "-b:a", "192k", "-ac", "2")
        }

        // Filters
        if (filterComplex != null) {
            args += listOf("-filter_complex", filterComplex, "-map", vMap, "-map", aMap)
            if (timedAdjustments != null && !segmentInputs && !p.sourceHasAudio) args += "-shortest"
        } else {
            // simple scale, optionally followed by color adjustments and subtitles burn-in
            val sub = p.subtitlesAssPath
            val color = buildColorFilter(p.adjustments)
            val filters = mutableListOf<String>()
            if (geometry != null) filters += geometry
            filters += scale
            if (color != null) filters += color
            if (sub != null) {
                val esc = escapeForFilterPath(sub)
                filters += "subtitles='${esc}'"
            }
            val vf = filters.joinToString(",")
            args += listOf("-vf", vf)
            if (!p.sourceHasAudio) {
                args += listOf("-map", "0:v:0", "-map", "1:a:0", "-shortest")
            }
        }

        // Container options
        // Always specify container format when known to support temporary .part outputs where extension is not standard.
        if (p.chunkOutput) {
            // Chunks are joined by the concat demuxer and copied into the real container afterwards.
            // MPEG-TS is the container meant for that: it carries H.264/AAC, tolerates being cut and
            // appended at arbitrary points, and remuxes back out without touching the frames.
            // faststart would only relocate an index this file never keeps.
            args += listOf("-f", "mpegts")
        } else {
            c?.format?.let { fmt -> args += listOf("-f", fmt) }
            if (c?.fastStart == true) {
                args += listOf("-movflags", "+faststart")
            }
        }

        args += p.outputPath

        val preview = buildString {
            append("ffmpeg ")
            args.forEach { a ->
                // Quote paths/filters containing special characters
                val needQuote = a.any { it.isWhitespace() } || a.contains(":") || a.contains(";") || a.contains("=") || a.contains("[")
                if (needQuote) append('"').append(a.replace("\\", "\\\\")).append('"') else append(a)
                append(' ')
            }
        }.trim()

        return Result(args = args, preview = preview)
    }
}

private fun Double.formatSecs(): String {
    // Keep 3 decimal places for sub-second accuracy; force dot decimal regardless of locale
    return java.lang.String.format(java.util.Locale.US, "%.3f", this)
}

private fun escapeForFilterPath(path: String): String {
    // Escape for use inside ffmpeg filter arguments on Windows:
    // - escape backslashes
    // - escape drive letter colon and any other colons
    // - escape single quotes (we wrap in single quotes)
    return path
        .replace("\\", "\\\\")
        .replace(":", "\\:")
        .replace("'", "\\'")
}
