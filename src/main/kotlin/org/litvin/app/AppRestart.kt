package org.litvin.app

import java.io.File

/**
 * The restart after a language change (B-11 plan, section 4, E3-T21).
 *
 * The new process starts the Velopack launcher, not the JAR. The old process holds the instance lock (B-19) until
 * it exits. Thus the new process gets [ARGUMENT] and the environment variable [ENVIRONMENT], and then waits some
 * seconds for the lock before it shows "already running".
 */
object AppRestart {
    const val ARGUMENT = "--restart"
    const val ENVIRONMENT = "BANANASHOT_RESTART"

    /** How long a restarted process waits for the lock of the old process. */
    const val LOCK_WAIT_MILLIS = 15_000L

    /** True when this process is a restart. */
    fun isRestart(args: Array<String>): Boolean = args.contains(ARGUMENT) || System.getenv(ENVIRONMENT) == "1"

    /**
     * The EXE that starts the installed app, or null for a start from the development build. On macOS, it is the
     * launcher in `BananaShot.app/Contents/MacOS`, and [command] starts its bundle. The jpackage launcher
     * sets `jpackage.app-path`. Velopack installs it in `<install>\current`, and the stub `<install>\BananaShot.exe`
     * starts the current version. The stub is the better choice, because it also works after an update.
     */
    fun launcher(appPath: String? = System.getProperty("jpackage.app-path")): File? {
        val exe = appPath?.let(::File)?.takeIf { it.isFile } ?: return null
        val folder = exe.absoluteFile.parentFile ?: return exe
        val stub = folder.parentFile?.let { File(it, exe.name) }
        return if (folder.name.equals("current", ignoreCase = true) && stub?.isFile == true) stub else exe
    }

    /** Starts [launcher] as a new process. The path can have spaces and non-ASCII letters. */
    fun start(launcher: File) {
        ProcessBuilder(command(launcher))
            .directory(launcher.absoluteFile.parentFile)
            .also { it.environment()[ENVIRONMENT] = "1" }
            .start()
    }

    /**
     * The command of the new process. It keeps the window-size option of this process (B-11 E4-T8).
     *
     * On macOS, `open -n` starts the app bundle of the launcher as a new app, as the Dock does (B-13). Without
     * `-n`, `open` only shows the running app. `open` does not pass the environment, so `--env` gives [ENVIRONMENT].
     */
    internal fun command(
        launcher: File,
        windowSize: WindowSize? = WindowSize.requested(),
        osName: String = System.getProperty("os.name").orEmpty(),
    ): List<String> {
        val arguments = listOfNotNull(ARGUMENT, windowSize?.let { "${WindowSize.ARGUMENT}$it" })
        val bundle = launcher.absoluteFile.parentFile?.parentFile?.parentFile
        if (osName.startsWith("Mac", ignoreCase = true) && bundle != null && bundle.name.endsWith(".app") &&
            launcher.absoluteFile.parentFile.name == "MacOS"
        ) {
            return listOf("open", "-n", bundle.path, "--env", "$ENVIRONMENT=1", "--args") + arguments
        }
        return listOf(launcher.absolutePath) + arguments
    }
}
