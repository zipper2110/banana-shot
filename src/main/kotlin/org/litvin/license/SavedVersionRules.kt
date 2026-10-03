package org.litvin.license

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The last valid version rules file, in the data folder. The app uses it when it cannot read the
 * server. A failure to read or write the file shows no error to the user: it goes to the log only.
 */
class SavedVersionRules(dataFolder: File) {
    private val logger = KotlinLogging.logger {}

    val file: File = dataFolder.resolve(FILE_NAME)

    /** Validates the saved file with the same rules as a new file. A damaged file gives `null`. */
    @Synchronized
    fun load(): VersionRules? {
        if (!file.isFile) return null
        val body = try {
            file.readBytes()
        } catch (e: IOException) {
            logger.warn(e) { "Cannot read the saved version rules file $file" }
            return null
        }
        return when (val parse = VersionRulesParser.parse(body)) {
            is VersionRulesParse.Valid -> parse.rules
            else -> {
                logger.warn { "The saved version rules file $file is ignored: $parse" }
                null
            }
        }
    }

    /**
     * Parses [body]. Only a valid file replaces the saved file. A file that is not valid, or a file
     * with an unknown schema, keeps the saved file.
     */
    @Synchronized
    fun replaceIfValid(body: ByteArray): VersionRulesParse {
        val parse = VersionRulesParser.parse(body)
        if (parse is VersionRulesParse.Valid) write(body)
        return parse
    }

    private fun write(body: ByteArray) {
        var temporary: File? = null
        try {
            file.parentFile?.mkdirs()
            temporary = File.createTempFile(".$FILE_NAME", ".tmp", file.parentFile)
            temporary.writeBytes(body)
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            logger.warn(e) { "Cannot save the version rules file $file" }
        } finally {
            temporary?.delete()
        }
    }

    companion object {
        const val FILE_NAME = "version-policy.json"
    }
}
