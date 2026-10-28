package org.litvin.ui.commons

import org.litvin.i18n.tr
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

/**
 * The text of an error for the user (B-11 plan, section 3, and "Decisions", "Technical texts").
 *
 * The message of an exception is English, and often technical. Thus the user first reads a catalog text that tells
 * the cause, when the type of the exception tells it. The message of the exception follows as the detail, because the
 * author reads it in a report. An exception without a message shows only the catalog text.
 */
object ErrorTexts {
    /** The text of [failure]. A [text] of the place (for example "The app cannot read the project.") replaces the general text. */
    fun of(failure: Throwable, text: String? = null): String {
        val causes = generateSequence(failure) { it.cause }.take(MAX_CAUSES).toList()
        val cause = when {
            causes.any { it is AccessDeniedException } -> tr("error.accessDenied")
            causes.any { it is NoSuchFileException || (it is FileNotFoundException && !isAccessDenied(it)) } -> tr("error.fileMissing")
            causes.any { it is FileNotFoundException } -> tr("error.accessDenied")
            else -> null
        }
        val shown = cause ?: text ?: tr("error.unexpected")
        val detail = failure.message?.trim().orEmpty()
        return if (detail.isEmpty()) shown else tr("error.withDetail", "text" to shown, "detail" to detail)
    }

    /** Windows gives a [FileNotFoundException] with "(Access is denied)" for a file that the app cannot open. */
    private fun isAccessDenied(failure: FileNotFoundException): Boolean =
        failure.message.orEmpty().contains("Access is denied", ignoreCase = true)

    private const val MAX_CAUSES = 8
}
