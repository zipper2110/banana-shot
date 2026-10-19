package org.litvin.ui.commons

import org.litvin.i18n.DisplayFormats

/** A length in seconds with one decimal in the regional format, for example "7.3 s" or "7,3 s". */
internal fun formatSeconds(durationMs: Long): String = DisplayFormats.decimal(durationMs / 1_000.0, 1) + " s"
