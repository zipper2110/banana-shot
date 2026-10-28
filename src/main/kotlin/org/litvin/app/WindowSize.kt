package org.litvin.app

/**
 * A fixed size of the main window, for a check of the layout at one size, for example of a translation (B-11 E4-T8).
 *
 * Give it as the program argument `--window-size=1200x800` or as the VM option `-Dbananashot.windowSize=1200x800`.
 * The size is in Swing units: at a Windows display scale of 125 %, 1200 x 800 fills 1500 x 1000 screen pixels.
 * The option opens the window at this size and does not maximize it. The user can still change the size.
 */
data class WindowSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x$height"

    companion object {
        const val PROPERTY = "bananashot.windowSize"
        const val ARGUMENT = "--window-size="

        /** The size of the window at the first start: the size of the UI flow tests. */
        val DEFAULT = WindowSize(1200, 800)

        const val MIN = 200
        const val MAX = 20_000
        private val FORMAT = Regex("""\s*(\d{1,5})\s*[xX×]\s*(\d{1,5})\s*""")

        /** The size in [value], for example "1200x800", or null for a missing or a wrong value. */
        fun parse(value: String?): WindowSize? {
            val match = value?.let(FORMAT::matchEntire) ?: return null
            val (width, height) = match.destructured.toList().map(String::toInt)
            return WindowSize(width, height).takeIf { width in MIN..MAX && height in MIN..MAX }
        }

        /** The value of the program argument [ARGUMENT] in [args], or null. The value can be wrong. */
        fun argumentValue(args: Array<String>): String? =
            args.lastOrNull { it.startsWith(ARGUMENT) }?.removePrefix(ARGUMENT)

        /** The size of the property [PROPERTY], or null. */
        fun requested(): WindowSize? = parse(System.getProperty(PROPERTY))
    }
}
