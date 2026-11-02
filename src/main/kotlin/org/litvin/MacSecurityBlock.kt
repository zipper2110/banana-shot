package org.litvin

/**
 * Finds the macOS errors that occur when macOS security blocks a file of the application (B-13). This is the macOS
 * part of [WindowsSecurityBlock]. On macOS, a blocked file usually has a code signature that is missing or not valid,
 * for example after a change to the app folder.
 */
object MacSecurityBlock {
    /**
     * Parts of the dyld error text when macOS refuses a library: "missing code signature", "code signature ... not
     * valid for use in process", "library load disallowed by system policy", and "different Team IDs".
     */
    private val LOAD_BLOCK_TEXTS = listOf("code signature", "disallowed by system policy", "different team ids")

    /** macOS stops a program with a code signature that is not valid at once, with SIGKILL: exit code 128 + 9. */
    const val KILLED_EXIT_CODE = 137

    fun isMac(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        osName.startsWith("Mac", ignoreCase = true)

    /** True when [cause] (or one of its causes) is a library load that macOS refused. */
    fun isLoadBlock(cause: Throwable?): Boolean =
        generateSequence(cause) { it.cause }.any { error ->
            val message = error.message.orEmpty().lowercase()
            LOAD_BLOCK_TEXTS.any { it in message }
        }

    /**
     * True when a program stopped with SIGKILL and wrote nothing. The export runs ffmpeg with `-v info`, so an
     * ffmpeg that runs always writes text. No text and SIGKILL tell that macOS stopped ffmpeg before it ran.
     */
    fun isKilledAtStart(exitCode: Int, output: String): Boolean =
        exitCode == KILLED_EXIT_CODE && output.isBlank()
}
