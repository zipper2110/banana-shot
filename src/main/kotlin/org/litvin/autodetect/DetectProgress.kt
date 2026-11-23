package org.litvin.autodetect

/**
 * The progress of a run for the two progress bars of the dialog.
 * - [step], [steps]: the number of the stage that runs (from 1), and the count of the stages of this run.
 * - [stepPart]: the part of the stage that is done, 0 to 1. Null when it is not known.
 * - [totalPart]: the part of the run that is done, 0 to 1.
 * - [leftSeconds]: the estimate of the time to the end of the run. Null when there is no estimate yet.
 */
data class DetectProgress(val step: Int, val steps: Int, val stepPart: Double?, val totalPart: Double, val leftSeconds: Long?) {
    companion object {
        /**
         * The reference run time of each stage, as a part of the length of the video. The values are from the run of
         * a 96-minute video on the RTX 3060 (E23b). They give the size of each stage in the total bar, and the first
         * time estimate. The measured speed of the stage that runs then corrects the estimate.
         */
        private val WEIGHT = mapOf(
            DetectStage.MEDIA to 0.120,
            DetectStage.AUDIO to 0.005,
            DetectStage.IMAGE to 0.015,
            DetectStage.COURT to 0.027,
            DetectStage.BALL to 0.345,
            DetectStage.FLIGHTS to 0.012,
            DetectStage.POSE to 0.127,
            DetectStage.RALLIES to 0.001,
            DetectStage.WINNERS to 0.001,
        )

        /** The stages of a run without the ball analysis ("Rallies only"). */
        private val FAST = listOf(DetectStage.MEDIA, DetectStage.AUDIO, DetectStage.IMAGE, DetectStage.RALLIES)

        /** A stage reports a usable speed after this part of it. */
        private const val MIN_MEASURED_PART = 0.03

        /** A stage with no progress reports never shows as complete from the time estimate alone. */
        private const val MAX_TIMED_PART = 0.95

        /** The measured speed corrects the reference speed by this factor at most. */
        private const val MAX_SPEED_FACTOR = 4.0

        fun stages(ballPass: Boolean): List<DetectStage> = if (ballPass) DetectStage.entries else FAST

        private fun weight(stage: DetectStage) = WEIGHT.getValue(stage)

        fun of(state: DetectState.Running, nowMs: Long = System.currentTimeMillis()): DetectProgress {
            val stages = stages(state.ballPass)
            val stage = state.stage
            // A stage that is not in the list of this run (the script decides) counts as the nearest stage before it.
            val index = if (stage == null) -1 else stages.indexOfLast { it.ordinal <= stage.ordinal }
            if (index < 0) {
                val left = state.videoSeconds?.let { video -> (stages.sumOf(::weight) * video).toLong() }
                return DetectProgress(1, stages.size, null, 0.0, left)
            }
            val current = stages[index]
            val total = stages.sumOf(::weight)
            val done = stages.take(index).sumOf(::weight)
            val later = stages.drop(index + 1).sumOf(::weight)
            val elapsed = ((nowMs - state.stageStartedAtMs) / 1000.0).coerceAtLeast(0.0)
            val video = state.videoSeconds?.takeIf { it > 0 }
            val reported = state.progress

            // The seconds for one unit of weight: the length of the video, corrected by the measured speed.
            val measured = reported?.takeIf { it >= MIN_MEASURED_PART }?.let { elapsed / (weight(current) * it) }
            val speed = when {
                measured != null && video != null -> measured.coerceIn(video / MAX_SPEED_FACTOR, video * MAX_SPEED_FACTOR)
                measured != null -> measured
                else -> video
            }
            val stepPart = reported
                ?: video?.let { (elapsed / (weight(current) * it)).coerceIn(0.0, MAX_TIMED_PART) }
            val left = speed?.let { s ->
                val stepLeft = if (measured != null) {
                    elapsed / reported!! * (1 - reported)
                } else {
                    weight(current) * s * (1 - (stepPart ?: 0.0))
                }
                (stepLeft + later * s).toLong()
            }
            val totalPart = ((done + weight(current) * (stepPart ?: 0.0)) / total).coerceIn(0.0, 1.0)
            return DetectProgress(index + 1, stages.size, stepPart, totalPart, left)
        }
    }
}
