package org.litvin.export

import org.litvin.RenderJob

/**
 * The facts of an export that started to run, for the usage analytics (B-9). It has no path, name, or other user
 * data. The `app` package changes the facts into analytics events, so the export code does not use `analytics`.
 */
data class RenderRunFacts(
    val encoderLabel: String,
    val outWidth: Int,
    val outHeight: Int,
    val scoreboard: Boolean,
    val comments: Boolean,
    val statsCard: Boolean,
    val favoritesOnly: Boolean,
    val idleTrim: Boolean,
) {
    companion object {
        fun of(job: RenderJob) = RenderRunFacts(
            encoderLabel = job.encoderLabel,
            outWidth = job.outWidth,
            outHeight = job.outHeight,
            scoreboard = job.includeScoreboard,
            comments = job.includeComments,
            statsCard = job.statsCard != null || job.setSummaries.isNotEmpty(),
            favoritesOnly = job.favoriteOnly,
            idleTrim = job.idleTrim,
        )
    }
}

enum class RenderFailReason { SOURCE_MISSING, OUTPUT_WRITE, PROCESS_START, FFMPEG_EXIT, OTHER }

sealed interface RenderRunResult {
    /** [videoMs] is the length of the output video, or null when it is not known. */
    data class Completed(val runMs: Long, val videoMs: Long?) : RenderRunResult
    data class Failed(val reason: RenderFailReason) : RenderRunResult
    data object Canceled : RenderRunResult
}

/** Gets the start and the end of each export that started to run. A job canceled in the queue never starts. */
interface RenderRunListener {
    fun started(facts: RenderRunFacts)
    fun finished(facts: RenderRunFacts, result: RenderRunResult)
}
