package org.litvin.share

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.projects.ManifestIO
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.Sport
import org.litvin.shared.util.SafeFileName
import org.litvin.stats.StatsReport
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.I18n

/** A point of the project for the Share tab. [number] starts at 1. [scoreLabel] is null when the project has no scored points. */
data class SharePoint(
    val id: String,
    val number: Int,
    val startMs: Long,
    val endMs: Long,
    val favorite: Boolean,
    val scoreLabel: String?,
) {
    val lengthMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    val middleMs: Long get() = startMs + lengthMs / 2

    /** "Point 11 · 6:42 · Set 1 · 2–2", for the point list. */
    val label: String get() = listOfNotNull(tr("share.point", "number" to number), ShareProject.clock(startMs), scoreLabel).joinToString(" · ")
}

/**
 * The data of a project that the Share tab uses: the points, the names, and the presets of the text fields.
 * [finalScore] is null when the project has no scored points.
 */
data class ShareProject(
    val projectDir: String,
    val name: String,
    val sourcePath: String?,
    val sport: Sport,
    val score: ScoreV1,
    val points: List<SharePoint>,
    val finalScore: String?,
    val videoDate: LocalDate?,
) {
    val hasFavorites: Boolean get() = points.any { it.favorite }

    /** "Best points" when the project has favorites, else "Full match". */
    val defaultVideoType: ThumbnailVideoType
        get() = if (hasFavorites) ThumbnailVideoType.BEST_POINTS else ThumbnailVideoType.FULL_MATCH

    /** The first favorite point. Without favorites, the point near the middle of the match. */
    val defaultPoint: SharePoint? get() = points.firstOrNull { it.favorite } ?: points.getOrNull(points.size / 2)

    fun point(id: String?): SharePoint? = points.firstOrNull { it.id == id }

    /**
     * The preset of each field. [event] is the league and [club] is the club name of the last thumbnail. An empty preset
     * does not show. The round and the score have no preset: they change in each match.
     */
    fun presets(type: ThumbnailVideoType, event: String?, club: String? = null): Map<ThumbnailField, String> = mapOf(
        ThumbnailField.HEAD to type.words,
        ThumbnailField.KIND to matchKind(),
        ThumbnailField.TEAM1 to sideName(score.player1Name, 1),
        ThumbnailField.TEAM2 to sideName(score.player2Name, 2),
        ThumbnailField.EVENT to event.orEmpty().trim().take(ThumbnailField.EVENT.maxLength),
        ThumbnailField.COURT to court(),
        ThumbnailField.DATE to (videoDate?.let(::thumbnailDate) ?: ""),
        ThumbnailField.CLUB to club.orEmpty().trim().take(ThumbnailField.CLUB.maxLength),
    )

    /**
     * The sport name for padel and beach tennis, for example "PADEL". For tennis, "DOUBLES" when the names are teams such
     * as "Dima / Yana", else "SINGLES".
     */
    fun matchKind(): String = DisplayFormats.upper(
        when (sport) {
            Sport.PADEL, Sport.BEACH_TENNIS -> sport.title
            Sport.TENNIS -> if (isTeam(score.player1Name) && isTeam(score.player2Name)) tr("share.kind.doubles") else tr("share.kind.singles")
        },
    )

    private fun isTeam(name: String) = TEAM_SEPARATORS.any { it in name }

    /** The name of a side, or empty when the user did not type a name. */
    private fun sideName(name: String, side: Int): String {
        val trimmed = name.trim()
        val defaults = Sport.entries.flatMap { it.defaultSideNames(side) }.toSet()
        return if (trimmed in defaults) "" else trimmed.take(ThumbnailField.TEAM1.maxLength)
    }

    private fun court(): String {
        val title = score.scoreboard.title.trim()
        return if (title in ScoreboardSettingsV1.defaultTitles()) "" else title.take(ThumbnailField.COURT.maxLength)
    }

    /** The length of the exported video: the points for "Full match", the favorite points for "Best points". */
    fun videoLength(type: ThumbnailVideoType): String {
        val points = if (type == ThumbnailVideoType.BEST_POINTS && hasFavorites) points.filter { it.favorite } else points
        return clock(points.sumOf { it.lengthMs })
    }

    /** The PNG of [type] in [folder]: one file for each video type, so that a second save replaces the first file. */
    fun thumbnailFile(folder: File, type: ThumbnailVideoType): File =
        File(folder, "${SafeFileName.of(name, "thumbnail")}-${type.fileSuffix}.png")

    companion object {
        private val TEAM_SEPARATORS = listOf("/", "&", "+", " and ", " y ")

        /**
         * The date on the thumbnail, for example "19 Oct 2026". The thumbnail is a text of the video, so the month
         * name is in the app language (B-11: the video language is the app language until E5).
         */
        fun thumbnailDate(date: LocalDate): String =
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", I18n.language.locale)).replace(".", "")

        fun load(projectDir: String, manifestPath: String): ShareProject {
            val manifest = ManifestIO.read(manifestPath)
            val edl = EdlIO.readForProjectDir(projectDir)
            val score = ScoreIO.readForProjectDir(projectDir)
            val source = manifest.sourceVideo
            return build(projectDir, manifest.name, source, edl, score, source?.let(::fileDate))
        }

        fun build(projectDir: String, name: String, sourcePath: String?, edl: EdlV1, score: ScoreV1, videoDate: LocalDate?): ShareProject {
            val sorted = edl.points.sortedBy { it.startMs }
            // The score of each point needs the scoring. A project without scored points has no score labels.
            val report = runCatching { StatsReport.build(edl, score) }.getOrNull()?.takeIf { it.hasScoredPoints }
            val points = sorted.mapIndexed { index, point ->
                SharePoint(
                    id = point.id,
                    number = index + 1,
                    startMs = point.startMs.toLong(),
                    endMs = point.endMs.toLong(),
                    favorite = point.favorite,
                    scoreLabel = report?.let { scoreLabel(it, index) },
                )
            }
            val finalScore = report?.setScores()?.joinToString(" ") { it.replace('-', '–') }
            return ShareProject(projectDir, name, sourcePath, score.sport, score, points, finalScore, videoDate)
        }

        /** "Set 2 · 3–1" before the point, or "4–3" for a match of points without games. */
        private fun scoreLabel(report: StatsReport, index: Int): String? {
            val state = report.timeline.stateBefore(index)
            return when (report.score.rules.structure) {
                MatchStructure.TOTAL_POINTS, MatchStructure.PLAIN_POINTS -> "${state.p1Pts}–${state.p2Pts}"
                else -> tr("override.set", "number" to state.setsP1 + state.setsP2 + 1) + " · ${state.gamesP1}–${state.gamesP2}"
            }
        }

        /** The date of the video file. A copy of a video usually keeps the time of the last change. */
        private fun fileDate(path: String): LocalDate? {
            val file = File(path)
            if (!file.isFile) return null
            return Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        /** "6:41" or "1:02:05". */
        fun clock(ms: Long): String {
            val seconds = ms / 1000
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
        }

        /** "0:04.6": the time with tenths of a second, for the moment in a point. */
        fun clockTenths(ms: Long): String = "${clock(ms)}.${(ms % 1000) / 100}"
    }
}
