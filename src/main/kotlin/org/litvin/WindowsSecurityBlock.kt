package org.litvin

/** Finds the Windows errors that occur when Windows security blocks a file of the application (B-22). */
object WindowsSecurityBlock {
    /**
     * 225 and 226: a virus scanner. 1260: a group policy. 4550-4559: Application Control, for example
     * Smart App Control blocks an unsigned EXE or DLL with error 4551.
     */
    private val BLOCK_ERRORS = setOf(225, 226, 1260) + (4550..4559)

    private val createProcessError = Regex("""CreateProcess error=(\d+)""")

    fun isBlockError(code: Int?): Boolean = code != null && code in BLOCK_ERRORS

    /** The Windows error of a failed process start, from the Java message "CreateProcess error=4551, ...". */
    fun createProcessError(cause: Throwable): Int? =
        generateSequence(cause) { it.cause }
            .firstNotNullOfOrNull { createProcessError.find(it.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }
}
