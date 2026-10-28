package org.litvin.export

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.I18n
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.scoring.Sport
import org.litvin.stats.MatchStat
import org.litvin.stats.StatRow
import org.litvin.stats.StatRows
import org.litvin.stats.StatValue
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsCardContent
import org.litvin.stats.StatsSettingsV1

/**
 * The fit check of the video texts (B-11 E3-T19, E4-T8). It draws each scoreboard style and each page of the
 * statistics card with the texts of a language, and reports each text that does not fit:
 *
 * - a text that the layout cut with "…";
 * - a text with a smaller font than the same text place in English;
 * - a text that goes outside the smallest box under it;
 * - a text that covers another text.
 *
 * The scenes use the default texts of the language: the side names, the title, the deciding-point badges, and the
 * names of all statistics. The values are the widest values of the card.
 */
internal object VideoTextFit {
    data class Problem(val place: String, val text: String, val reason: String) {
        override fun toString() = "$place: \"$text\" $reason"
    }

    /** A named scene. The same [place] in English and in the checked language has the same items in the same order. */
    private data class Placed(val place: String, val scene: ScoreboardScene)

    fun check(language: AppLanguage): List<Problem> {
        val english = I18n.inVideo(AppLanguage.ENGLISH) { scenes() }.associate { it.place to it.scene }
        return I18n.inVideo(language) { scenes() }.flatMap { (place, scene) -> problems(place, scene, english[place]) }
    }

    private fun scenes(): List<Placed> = scoreboards() + statsCards()

    private fun scoreboards(): List<Placed> = buildList {
        for (sport in Sport.entries) {
            val badges = sport.deuceRules(DeuceRule.ADVANTAGE).mapNotNull { rule -> sport.decidingPointBadge(rule)?.let { rule to it } }
            for (style in ScoreboardStyleId.entries) {
                add(Placed("$sport $style", ScoreboardLayouts.scene(display(sport, badge = null), ScoreboardSettingsV1(style = style))))
                for ((rule, badge) in badges) {
                    add(Placed("$sport $style $rule badge", ScoreboardLayouts.scene(display(sport, badge), ScoreboardSettingsV1(style = style))))
                }
            }
        }
    }

    private fun display(sport: Sport, badge: String?) = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L,
            endMs = 1_000L,
            text = "",
            p1Name = sport.videoSideName("", 1),
            p2Name = sport.videoSideName("", 2),
            p1ColorHex = "#2E7DFF",
            p2ColorHex = "#FF4D4D",
            p1Pts = 3,
            p2Pts = 4,
            gamesP1 = 6,
            gamesP2 = 6,
            completedSets = listOf(7 to 6, 6 to 7),
            server = 1,
            badge = badge,
        ),
    )

    /** The card with all statistics: the match card and a set card, for each deuce rule. */
    private fun statsCards(): List<Placed> = buildList {
        val settings = StatsSettingsV1()
        for (rule in DeuceRule.entries) {
            val rules = MatchRulesV1(deuce = rule)
            val rows = MatchStat.entries.map { stat -> widestRow(stat, StatRows.label(stat, settings, rules)) }
            val names = PerPlayer(Sport.PADEL.videoSideName("", 1), Sport.PADEL.videoSideName("", 2))
            for (scope in listOf(0, 3)) {
                val content = StatsCardContent(
                    names = names,
                    colors = PerPlayer(0x2E7DFF, 0xFF4D4D),
                    title = if (scope == 0) org.litvin.i18n.tr("stats.card.match") else org.litvin.i18n.tr("stats.card.set", "number" to scope),
                    score = if (scope == 0) "7-6   6-7   7-6" else "7-6",
                    rows = rows,
                    credit = ScoreboardSettingsV1.APP_CREDIT,
                )
                StatsCard.pages(content).forEachIndexed { page, scene -> add(Placed("stats card $rule scope $scope page ${page + 1}", scene)) }
            }
        }
    }

    /** A row with the widest values of its kind. The time rows have one value for both players. */
    private fun widestRow(stat: MatchStat, label: String): StatRow = when (stat.group) {
        org.litvin.stats.StatGroup.TIME -> StatRow(stat, shared = StatValue("1:58:59"), label = label)
        else -> {
            val value = StatValue(listOf("188/188", DisplayFormats.percent(100)).maxBy { ScoreboardFonts.textWidth(it, "Segoe UI", true, 54.0) })
            StatRow(stat, values = PerPlayer(value, value), bar = PerPlayer(1.0, 1.0), label = label)
        }
    }

    /** The problems of one [scene]. [english] is the same scene with the English texts, or null. */
    internal fun problems(place: String, scene: ScoreboardScene, english: ScoreboardScene?): List<Problem> {
        val labels = scene.items.filterIsInstance<SceneItem.Label>().filter { it.text.isNotBlank() }
        val englishLabels = english?.items?.filterIsInstance<SceneItem.Label>()?.filter { it.text.isNotBlank() }
        val boxes = scene.items.filterIsInstance<SceneItem.Box>()
        val found = mutableListOf<Problem>()
        labels.forEachIndexed { index, label ->
            val bounds = bounds(label)
            if ('…' in label.text) found += Problem(place, label.text, "is cut")
            val englishLabel = englishLabels?.getOrNull(index)
            if (englishLabels?.size == labels.size && englishLabel != null && label.size < englishLabel.size - 0.01) {
                found += Problem(place, label.text, "has a smaller font than in English (${label.size} < ${englishLabel.size})")
            }
            if (bounds.left < -TOLERANCE || bounds.right > scene.width + TOLERANCE) {
                found += Problem(place, label.text, "goes outside the scene (${bounds.left.toInt()}..${bounds.right.toInt()} of ${scene.width.toInt()})")
            }
            boxUnder(label, boxes)?.let { box ->
                if (bounds.left < box.x - TOLERANCE || bounds.right > box.x + box.width + TOLERANCE) {
                    found += Problem(
                        place, label.text,
                        "goes outside its box (${bounds.left.toInt()}..${bounds.right.toInt()}, box ${box.x.toInt()}..${(box.x + box.width).toInt()})",
                    )
                }
            }
            labels.drop(index + 1).forEach { other ->
                if (bounds.overlaps(bounds(other))) found += Problem(place, label.text, "covers \"${other.text}\"")
            }
        }
        return found
    }

    /** The smallest box that has the anchor point of [label] in it, or null. */
    private fun boxUnder(label: SceneItem.Label, boxes: List<SceneItem.Box>): SceneItem.Box? {
        val middle = bounds(label).middleY
        return boxes
            .filter { label.x >= it.x && label.x <= it.x + it.width && middle >= it.y && middle <= it.y + it.height }
            .minByOrNull { it.width * it.height }
    }

    private class Bounds(val left: Double, val right: Double, val top: Double, val bottom: Double) {
        val middleY: Double get() = (top + bottom) / 2

        fun overlaps(other: Bounds): Boolean =
            minOf(right, other.right) - maxOf(left, other.left) > TOLERANCE &&
                minOf(bottom, other.bottom) - maxOf(top, other.top) > TOLERANCE
    }

    /**
     * The part of the line box that the letters fill. [SceneItem.Label.size] is the height of the line box, and the
     * capital letters fill about the middle 60 % of it, so two lines close together do not count as a cover.
     */
    private fun bounds(label: SceneItem.Label): Bounds {
        // The letter spacing also comes after the last letter.
        val width = ScoreboardFonts.textWidth(label.text, label.font, label.bold, label.size, label.spacing) - label.spacing
        val (left, right) = when (label.anchor) {
            TextAnchor.TOP_LEFT, TextAnchor.MIDDLE_LEFT -> label.x to label.x + width
            TextAnchor.TOP_RIGHT, TextAnchor.MIDDLE_RIGHT -> label.x - width to label.x
            TextAnchor.CENTER -> label.x - width / 2 to label.x + width / 2
        }
        val middle = when (label.anchor) {
            TextAnchor.TOP_LEFT, TextAnchor.TOP_RIGHT -> label.y + label.size / 2
            else -> label.y
        }
        val half = label.size * INK_SHARE / 2
        return Bounds(left, right, middle - half, middle + half)
    }

    private const val TOLERANCE = 1.0
    private const val INK_SHARE = 0.6
}
