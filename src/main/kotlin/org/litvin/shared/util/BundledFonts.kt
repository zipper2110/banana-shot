package org.litvin.shared.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The open fonts of the scoreboards, the statistics card, and the comments (B-13, step 10). They are in
 * `src/main/resources/fonts` with their licenses and sources. Windows and macOS then draw the same text: before,
 * the styles used fonts of Windows, and a Mac does not have Segoe UI, Consolas, or Ink Free.
 *
 * - Java measures and draws the text with the same fonts: [registerInJava].
 * - libass (the export) and mpv (the preview) need a folder on the disk: [directory].
 */
object BundledFonts {
    private val logger = KotlinLogging.logger {}

    /** The font files. Each family has a Regular and a Bold file, except the Black fonts. */
    val FILES = listOf(
        "Inter-Regular.ttf", "Inter-Bold.ttf", "Inter-Black.ttf",
        "LiberationSans-Regular.ttf", "LiberationSans-Bold.ttf",
        "ArchivoBlack-Regular.ttf",
        "Gelasio-Regular.ttf", "Gelasio-Bold.ttf",
        "FiraSans-Regular.ttf", "FiraSans-Bold.ttf",
        "OpenSans-Regular.ttf", "OpenSans-Bold.ttf",
        "CascadiaMono-Regular.ttf", "CascadiaMono-Bold.ttf",
        "Caveat-Regular.ttf", "Caveat-Bold.ttf",
    )

    /** The family names in the font files. The ASS files and Java use these names. */
    const val INTER = "Inter"
    const val INTER_BLACK = "Inter Black"
    const val LIBERATION_SANS = "Liberation Sans"
    const val ARCHIVO_BLACK = "Archivo Black"
    const val GELASIO = "Gelasio"
    const val FIRA_SANS = "Fira Sans"
    const val OPEN_SANS = "Open Sans"
    const val CASCADIA_MONO = "Cascadia Mono"
    const val CAVEAT = "Caveat"

    private const val RESOURCE_FOLDER = "/fonts/"

    @Volatile
    private var registered = false

    @Volatile
    private var folder: File? = null

    /**
     * Registers each font in Java one time. Call it before the first layout. A family that the computer also has
     * keeps the font of the computer in Java ([GraphicsEnvironment.registerFont] returns false); the log tells it.
     */
    fun registerInJava() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            for (name in FILES) {
                try {
                    val font = resource(name).use { Font.createFont(Font.TRUETYPE_FONT, it) }
                    if (!environment.registerFont(font)) {
                        logger.info { "The computer has the font ${font.fontName}. Java uses the font of the computer." }
                    }
                } catch (failure: Exception) {
                    logger.warn(failure) { "Cannot register the bundled font $name." }
                }
            }
            registered = true
        }
    }

    /**
     * The folder with the font files for libass and mpv, in the app data folder. The files are written one time
     * after each change of the app: a file with a different size is written again.
     */
    fun directory(appData: File = ApplicationLayout.current().appDataDirectory): File {
        folder?.let { return it }
        return synchronized(this) {
            folder ?: extract(appData.resolve("fonts")).also { folder = it }
        }
    }

    /** [directory], or null when the files cannot be written. Then libass and mpv use the fonts of the computer. */
    fun directoryOrNull(): File? = try {
        directory()
    } catch (failure: Exception) {
        logger.warn(failure) { "Cannot write the bundled fonts to the app data folder." }
        null
    }

    private fun extract(target: File): File {
        Files.createDirectories(target.toPath())
        for (name in FILES) {
            val bytes = resource(name).use { it.readBytes() }
            val file = target.resolve(name)
            if (file.isFile && file.length() == bytes.size.toLong() && file.readBytes().contentEquals(bytes)) continue
            val temporary = File.createTempFile(".$name", ".tmp", target)
            try {
                temporary.writeBytes(bytes)
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
        return target
    }

    private fun resource(name: String) = requireNotNull(BundledFonts::class.java.getResourceAsStream(RESOURCE_FOLDER + name)) {
        "The font $name is not in the app."
    }
}
