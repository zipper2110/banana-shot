package org.litvin

import java.io.File
import java.net.URI

data class RuntimeLayout(
    val appHome: File,
    val nativeRoot: File,
    val mpvDirectory: File?,
    val ffmpegExecutable: String,
    val ffprobeExecutable: String,
    val packagedLauncher: File?,
    val appDataDirectory: File,
)

class ApplicationLayoutResolver(
    private val properties: Map<String, String> = System.getProperties()
        .stringPropertyNames()
        .associateWith { System.getProperty(it) },
    private val environment: Map<String, String> = System.getenv(),
    private val codeSourceLocation: URI? = ApplicationLayoutResolver::class.java.protectionDomain
        ?.codeSource
        ?.location
        ?.toURI(),
    private val workingDirectory: File = File("."),
    /** The libmpv folder of Homebrew on Apple Silicon. Only development on macOS uses it. */
    private val homebrewLibDirectory: File = File("/opt/homebrew/lib"),
) {
    fun resolve(): RuntimeLayout {
        val appHome = resolveAppHome()
        val nativeRoot = File(appHome, "natives/${platformFolder()}")
        val ffmpegExecutable = resolveExecutable(
            propertyName = "bananashot.ffmpegPath",
            environmentName = "FFMPEG_PATH",
            bundledFile = File(nativeRoot, "ffmpeg/bin/${executable("ffmpeg")}"),
            developmentFile = File(workingDirectory, "target/native/${platformFolder()}/ffmpeg/bin/${executable("ffmpeg")}"),
            fallbackCommand = if (isWindows()) "ffmpeg.exe" else "ffmpeg",
        )
        val ffprobeExecutable = resolveFfprobe(nativeRoot, ffmpegExecutable)

        return RuntimeLayout(
            appHome = appHome,
            nativeRoot = nativeRoot,
            mpvDirectory = resolveMpvDirectory(nativeRoot),
            ffmpegExecutable = ffmpegExecutable,
            ffprobeExecutable = ffprobeExecutable,
            packagedLauncher = File(appHome, "${AppInfo.NAME}.exe").takeIf { it.isFile },
            appDataDirectory = resolveAppDataDirectory(),
        )
    }

    private fun resolveAppHome(): File {
        value("bananashot.appDir")?.let { return File(it).absoluteFile.normalize() }

        val location = codeSourceLocation?.let(::File)?.absoluteFile?.normalize()
        if (location != null) {
            val container = if (location.isFile) location.parentFile else location
            if (container?.name.equals("app", ignoreCase = true)) {
                return container.parentFile
            }
        }
        return workingDirectory.absoluteFile.normalize()
    }

    /*
     * The order for each native tool: the system property, the bundled file, the environment variable, the file in the
     * build folder (`target/native`), the fallback.
     * The bundled file comes before the environment variable, because an installed app must use its own signed and
     * pinned build (B-22). A variable that a different program set on the user's PC must not replace it. In development,
     * the app home has no bundle, so the variable still applies.
     */

    private fun resolveMpvDirectory(nativeRoot: File): File? {
        value("bananashot.mpvPath")?.let { return File(it).absoluteFile.normalize() }
        File(nativeRoot, "mpv").takeIf { it.isDirectory }?.let { return it }
        environment["MPV_PATH"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absoluteFile.normalize()
        }
        File(workingDirectory, "target/native/${platformFolder()}/mpv").takeIf { it.isDirectory }?.let { return it }
        // Development on macOS before the pinned macOS build exists (B-13): `brew install mpv`.
        return homebrewLibDirectory.takeIf { isMac() && File(it, "libmpv.dylib").isFile }
    }

    private fun resolveFfprobe(nativeRoot: File, ffmpegExecutable: String): String {
        value("bananashot.ffprobePath")?.let { return File(it).absolutePath }
        val bundled = File(nativeRoot, "ffmpeg/bin/${executable("ffprobe")}")
        if (bundled.isFile) return bundled.absolutePath
        environment["FFPROBE_PATH"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absolutePath
        }

        val ffmpegFile = File(ffmpegExecutable)
        if (ffmpegFile.isAbsolute || ffmpegFile.parentFile != null) {
            val sibling = File(ffmpegFile.parentFile, if (isWindows()) "ffprobe.exe" else "ffprobe")
            if (sibling.isFile) return sibling.absolutePath
        }
        return if (isWindows()) "ffprobe.exe" else "ffprobe"
    }

    private fun resolveExecutable(
        propertyName: String,
        environmentName: String,
        bundledFile: File,
        developmentFile: File,
        fallbackCommand: String,
    ): String {
        value(propertyName)?.let { return File(it).absolutePath }
        if (bundledFile.isFile) return bundledFile.absolutePath
        environment[environmentName]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absolutePath
        }
        // The pinned build of Get-NativeDependencies.ps1 (Windows) or build-dev-ffmpeg.sh (macOS).
        if (developmentFile.isFile) return developmentFile.absoluteFile.normalize().path
        return fallbackCommand
    }

    private fun resolveAppDataDirectory(): File {
        value("bananashot.appDataDir")?.let { return File(it).absoluteFile.normalize() }
        environment["BANANASHOT_APP_DATA_DIR"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absoluteFile.normalize()
        }
        environment["APPDATA"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it, AppInfo.NAME)
        }
        return File(properties["user.home"] ?: ".", ".bananashot")
    }

    private fun value(name: String): String? = properties[name]?.trim()?.takeIf { it.isNotEmpty() }

    private fun isWindows(): Boolean = properties["os.name"]
        ?.contains("windows", ignoreCase = true)
        ?: false

    private fun isMac(): Boolean = properties["os.name"]
        ?.startsWith("mac", ignoreCase = true)
        ?: false

    /** The folder of the native files: `windows-x64`, or `macos-arm64` on macOS (Apple Silicon only, B-13). */
    private fun platformFolder(): String = if (isMac()) "macos-arm64" else "windows-x64"

    private fun executable(name: String): String = if (isWindows()) "$name.exe" else name
}

object ApplicationLayout {
    @Volatile
    private var cached: RuntimeLayout? = null

    fun current(): RuntimeLayout = cached ?: synchronized(this) {
        cached ?: ApplicationLayoutResolver().resolve().also { cached = it }
    }

    internal fun resetForTests() {
        cached = null
    }
}
