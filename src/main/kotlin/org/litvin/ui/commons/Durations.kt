package org.litvin.ui.commons

import java.util.Locale

/** A length in seconds with one decimal, for example "7.3 s". */
internal fun formatSeconds(durationMs: Long): String = String.format(Locale.ROOT, "%.1f s", durationMs / 1_000.0)
