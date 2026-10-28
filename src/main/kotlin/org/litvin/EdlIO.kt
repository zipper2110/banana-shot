package org.litvin.points

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.JsonFileIO
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * EDL (Edit Decision List) schema v1 and JSON read/write helpers for points-only scope (task 2.3).
 * Now includes ID backfill and duplicate repair per task 2.16.
 *
 * Storage:
 * - File name: edl.json placed in the project directory (next to the .trproj manifest)
 * - JSON library: Jackson Kotlin module (same configuration as ManifestIO)
 */

// Data model

data class PointV1(
    val id: String = "", // Backfill for legacy entries without id (2.16)
    val startMs: Int,
    val endMs: Int,
    val label: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    /** The instant replays after the point, sorted by start time (B-50). An empty list is not written. */
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val replays: List<ReplayV1> = emptyList(),
)

/** How the video goes into a replay and out of it (B-50, decision 3 of `docs/replay/b-50-tasks.md`). */
enum class ReplayEffect {
    /** A dip to black: the video fades out, then the next picture fades in. */
    @JsonEnumDefaultValue
    FADE,

    /** A dip to white. */
    FLASH,

    /** No effect. */
    CUT,
}

/**
 * The zoom frame of a replay, as fractions of the frame after the crop and the rotation of the Transform tab.
 * [x] and [y] are the upper-left corner. The zoom frame has the aspect of the frame, so [width] is also the
 * fraction of the frame height. Thus the zoom factor is 1 / [width].
 */
data class ReplayZoom(val x: Double, val y: Double, val width: Double)

/**
 * A still frame inside a replay (decision 22): the replay stops on the frame at [atMs] of the source for [durationMs],
 * then plays on. [atMs] is inside the part of the replay. The sound is silent during the still frame.
 */
data class ReplayFreeze(val atMs: Int, val durationMs: Int = Replays.DEFAULT_FREEZE_MS)

/**
 * One instant replay of a part of a point (B-50). The part is in source time. [Replays] gives the limits.
 * The replay plays the part once at [speed], right after the end of its point. A replay has no repeats (decision 21).
 */
data class ReplayV1(
    val startMs: Int,
    val endMs: Int,
    /** Null is no zoom. */
    val zoom: ReplayZoom? = null,
    val speed: Double = Replays.DEFAULT_SPEED,
    /** True mutes the sound. Otherwise the sound slows down with the video, with a lower pitch (decision 4). */
    val muted: Boolean = false,
    val effect: ReplayEffect = ReplayEffect.FADE,
    /** Shows the "REPLAY" badge on the video during the replay. */
    val showBadge: Boolean = true,
    /**
     * The text of the badge. Null is the default label, which follows the place of the replay in its point:
     * "Replay" for the first replay, "Replay 2" for the second, and so on (decision 19). The label does not change the
     * replay number in the lists.
     */
    val label: String? = null,
    /** The still frame of the replay. Null is no still frame, the default. */
    val freeze: ReplayFreeze? = null,
    /**
     * The comments that show only during this replay (decision 24). The [CommentV1.startMs] of each comment is a source
     * time inside the part, so the comment stays on the same moment of play when the speed or the edges change. Its
     * [CommentV1.durationMs] is a time of the replay play, so the comment shows for that time at each speed. The ids
     * count inside the replay. An empty list is not written.
     */
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comments: List<CommentV1> = emptyList(),
    /** True shows the point comments of the part during the replay, as before decision 24. */
    val showPointComments: Boolean = true,
)

/**
 * How the video shows a comment (decided on 2026-10-05). `CommentAss` draws each style.
 * The text, the card and the outline use black or white, whichever has the higher contrast with the comment color.
 */
enum class CommentStyle {
    /**
     * No backdrop: the text has the comment color, an outline and a soft shadow.
     * Comments saved before the styles existed use it, and new comments use it until the user selects a style.
     */
    @JsonEnumDefaultValue
    OUTLINE,

    /** The text has the comment color, on a rounded card that fits the text. */
    CARD,

    /** Each line is on a rounded pill with the comment color. */
    PILL,
}

data class CommentV1(
    val id: Int,
    val startMs: Int,
    val durationMs: Int,
    val text: String,
    val colorHex: String = DEFAULT_COLOR_HEX,
    val style: CommentStyle = CommentStyle.OUTLINE,
    /** The place of the comment in the frame. Null is the default place: centered, in the lower third. */
    val position: CommentPosition? = null,
    /** The size of the comment, in percent of the default size (B-51). */
    val sizePercent: Int = DEFAULT_SIZE_PERCENT,
    /** The opacity of the whole comment, in percent (B-51). The shadows and the card are also transparent. */
    val opacityPercent: Int = 100,
    /** The rotation of the comment around the center of its box, in whole degrees (B-51). Positive is clockwise. */
    val rotationDeg: Int = 0,
) {
    companion object {
        /** The color of a new comment: white. */
        const val DEFAULT_COLOR_HEX = "#FFFFFF"
        const val DEFAULT_SIZE_PERCENT = 100
        const val MIN_SIZE_PERCENT = 50
        const val MAX_SIZE_PERCENT = 200
        const val MIN_OPACITY_PERCENT = 20
        const val MIN_ROTATION_DEG = -180
        const val MAX_ROTATION_DEG = 180
    }
}

/**
 * The place of a comment in the frame, as fractions of the frame size (0 to 1).
 * [x] is the horizontal center of the comment. [y] is the lower edge of the comment.
 * The user sets it when they drag the comment on the video (B-51).
 */
data class CommentPosition(val x: Double, val y: Double) {
    companion object {
        /** The place of a comment without a position. */
        val DEFAULT = CommentPosition(0.5, 0.82)
    }
}

data class CommentDefaultsV1(
    val colorHex: String = "#FFFFFF",
)

/**
 * EDL v1 container. Version is fixed to 1 for this schema.
 */
data class EdlV1(
    val points: List<PointV1> = emptyList(),
    val version: Int = 1,
    val comments: List<CommentV1> = emptyList(),
    val commentDefaults: CommentDefaultsV1 = CommentDefaultsV1(),
    val nextCommentId: Int = 1,
)

/**
 * JSON read/write for edl.json with Jackson Kotlin module.
 * - Pretty prints and omits nulls.
 * - Ignores unknown fields on read.
 */
object EdlIO {
    private val logger = KotlinLogging.logger {}

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Generate a new unique lowercase UUIDv4 string (2.16). */
    fun generateId(): String = UUID.randomUUID().toString().lowercase()

    /** Ensure all points have unique, non-blank ids. Repairs missing/duplicate ids deterministically. */
    fun ensureUniqueIds(points: List<PointV1>): Pair<List<PointV1>, Boolean> {
        val seen = HashSet<String>()
        var changed = false
        val repaired = points.map { p ->
            var pid = p.id
            if (pid.isBlank() || seen.contains(pid)) {
                var newId: String
                do { newId = generateId() } while (seen.contains(newId))
                pid = newId
                changed = true
            }
            seen.add(pid)
            if (pid == p.id) p else p.copy(id = pid)
        }
        return repaired to changed
    }

    fun normalizeColorHex(colorHex: String?): String? {
        val match = colorHex?.trim()?.let(COLOR_HEX_PATTERN::matchEntire) ?: return null
        return "#${match.groupValues[1].uppercase(Locale.ROOT)}"
    }

    /** Remove invalid comments and repair duplicate/non-positive numeric IDs deterministically. */
    fun normalizeComments(edl: EdlV1): EdlV1 {
        val validComments = edl.comments.filter { comment ->
            comment.startMs >= 0 && comment.durationMs > 0 && comment.text.isNotBlank()
        }
        val retainedIds = HashSet<Int>()
        val firstValidIds = validComments.map { comment ->
            comment.id.takeIf { it > 0 && retainedIds.add(it) }
        }
        var replacementId = (retainedIds.maxOrNull() ?: 0) + 1
        val repaired = validComments.mapIndexed { index, comment ->
            val id = firstValidIds[index] ?: replacementId++
            val color = normalizeColorHex(comment.colorHex) ?: DEFAULT_COMMENT_COLOR
            comment.copy(
                id = id,
                colorHex = color,
                position = normalizePosition(comment.position),
                sizePercent = normalizeSizePercent(comment.sizePercent),
                opacityPercent = normalizeOpacityPercent(comment.opacityPercent),
                rotationDeg = normalizeRotationDeg(comment.rotationDeg),
            )
        }
        val maxCommentId = repaired.maxOfOrNull { it.id } ?: 0
        val normalizedDefault = normalizeColorHex(edl.commentDefaults.colorHex) ?: DEFAULT_COMMENT_COLOR
        val nextCommentId = maxOf(edl.nextCommentId, maxCommentId + 1, replacementId, 1)
        return edl.copy(
            comments = repaired,
            commentDefaults = CommentDefaultsV1(normalizedDefault),
            nextCommentId = nextCommentId,
        )
    }

    /** A position outside the frame moves to the nearest edge. A position that is not a number is removed. */
    internal fun normalizePosition(position: CommentPosition?): CommentPosition? {
        if (position == null || !position.x.isFinite() || !position.y.isFinite()) return null
        return CommentPosition(position.x.coerceIn(0.0, 1.0), position.y.coerceIn(0.0, 1.0))
    }

    internal fun normalizeSizePercent(percent: Int): Int =
        percent.coerceIn(CommentV1.MIN_SIZE_PERCENT, CommentV1.MAX_SIZE_PERCENT)

    internal fun normalizeOpacityPercent(percent: Int): Int = percent.coerceIn(CommentV1.MIN_OPACITY_PERCENT, 100)

    /** An angle outside -180..180 turns into that range, for example 270 gives -90. */
    internal fun normalizeRotationDeg(degrees: Int): Int {
        val turned = Math.floorMod(degrees + 180, 360) - 180
        return if (turned == -180 && degrees > 0) 180 else turned
    }

    /** Returns absolute path to edl.json for a given project directory. */
    fun edlFilePath(projectDir: String): String = File(projectDir, "edl.json").absolutePath

    /** Returns the project directory given a manifest file path. */
    fun projectDirFromManifest(manifestFilePath: String): String = File(manifestFilePath).parentFile.absolutePath

    /** Write EDL to the given file path. Verifies/repairs unique ids before persisting. */
    fun write(edlFilePath: String, edl: EdlV1) {
        val (fixedPoints, changed) = ensureUniqueIds(edl.points)
        if (changed) {
            logger.info { "Repaired missing/duplicate point ids before save; changes will be persisted" }
        }
        JsonFileIO.writeAtomically(mapper, edlFilePath, normalizeComments(edl.copy(points = fitReplays(fixedPoints))))
    }

    /** Keeps the replays of each point inside the limits of [Replays] (B-50). */
    internal fun fitReplays(points: List<PointV1>): List<PointV1> = points.map(Replays::fitted)

    /** Read EDL from the given file path; returns empty EdlV1 if file does not exist. Repairs ids if needed. */
    fun read(edlFilePath: String): EdlV1 {
        val f = File(edlFilePath)
        if (!f.exists()) return EdlV1(emptyList(), 1)
        val raw: EdlV1 = JsonFileIO.read(mapper, f.path, EdlV1::class.java)
        val (fixedPoints, changed) = ensureUniqueIds(raw.points)
        if (changed) {
            logger.info { "Backfilled/repaired point ids while loading; will persist on next autosave" }
        }
        return normalizeComments(raw.copy(points = fitReplays(fixedPoints)))
    }

    /** Convenience: read EDL for a given project directory. */
    fun readForProjectDir(projectDir: String): EdlV1 = read(edlFilePath(projectDir))

    /** Convenience: write EDL for a given project directory. */
    fun writeForProjectDir(projectDir: String, edl: EdlV1) = write(edlFilePath(projectDir), edl)

    private val COLOR_HEX_PATTERN = Regex("^#?([0-9A-Fa-f]{6})$")
    private const val DEFAULT_COMMENT_COLOR = CommentV1.DEFAULT_COLOR_HEX
}
