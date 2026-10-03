package org.litvin.export

import org.litvin.RenderJob

/** What the user can do after a failed export. */
object ExportFailureAdvice {
    const val HARDWARE_ENCODER =
        "The export used a hardware encoder. Select Advanced and then Software (x264), and export again. " +
            "The software encoder is slower, but it works on all PCs."

    /**
     * The advice for a failed [job], or null when the app has no advice.
     * A hardware encoder can pass the test encode and still fail, for example after a graphics driver update.
     * The software encoder has no such advice, because its failures have other causes.
     */
    fun of(job: RenderJob): String? =
        HARDWARE_ENCODER.takeIf { ExportEncoder.entries.any { it.hardware && it.jobLabel == job.encoderLabel } }
}
