package org.litvin.license.update

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.license.online.UpdateTrust
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads the setup EXE. Tests give a fake. */
fun interface SetupDownloader {
    /**
     * Writes the file at [url] to [target]. Calls [progress] with the bytes that arrived and the size
     * of the file (null when the server does not give it). Throws when the download fails.
     */
    fun download(url: URI, target: File, progress: (bytes: Long, totalBytes: Long?) -> Unit)
}

/** Starts the setup EXE. Tests give a fake. */
fun interface SetupLauncher {
    fun start(setup: File)
}

/**
 * The download with `HttpClient`. It follows redirects, because GitHub sends release files from
 * another host.
 *
 * The spec requires the same proxy and the same trust as the read of the rules file. The proxy comes
 * from `java.net.useSystemProxies` in `main`. The trust is [UpdateTrust.production]: the bundled store
 * and `Windows-ROOT`.
 */
class HttpSetupDownloader(
    private val client: HttpClient = UpdateTrust.production.client(HttpClient.Redirect.NORMAL, Duration.ofSeconds(10)),
) : SetupDownloader {
    override fun download(url: URI, target: File, progress: (bytes: Long, totalBytes: Long?) -> Unit) {
        // The request sends no user ID and no analytics data (build-expiry-spec.md, "Privacy").
        val request = HttpRequest.newBuilder(url).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (response.statusCode() != 200) {
                throw IOException("The server answered HTTP ${response.statusCode()} for $url")
            }
            val total = response.headers().firstValueAsLong("Content-Length").let { if (it.isPresent) it.asLong else null }
            copy(body, target, total, progress)
        }
    }

    private fun copy(body: InputStream, target: File, total: Long?, progress: (Long, Long?) -> Unit) {
        Files.newOutputStream(target.toPath()).use { output ->
            val buffer = ByteArray(64 * 1024)
            var bytes = 0L
            while (true) {
                val count = body.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                bytes += count
                progress(bytes, total)
            }
            if (total != null && bytes != total) {
                throw IOException("The download stopped after $bytes of $total bytes")
            }
        }
    }
}

/** Starts the setup with no argument. Do not give `--silent`: then the setup does not start the new version. */
object ProcessSetupLauncher : SetupLauncher {
    override fun start(setup: File) {
        ProcessBuilder(setup.absolutePath).directory(setup.parentFile).start()
    }
}

/**
 * "Update and restart" (B-30, build-expiry-spec.md "Update and restart"): download the setup EXE of
 * the newest release, close the app, and start the setup as the last step. The Velopack setup asks
 * "Update" or "Cancel", stops the app, installs the new version, and starts it.
 *
 * The user interface calls [run] on a background thread and shows the progress. It works the same in
 * expired mode.
 */
class UpdateAndRestart(
    private val downloader: SetupDownloader = HttpSetupDownloader(),
    private val launcher: SetupLauncher = ProcessSetupLauncher,
    private val temporaryFolder: () -> File = { Files.createTempDirectory("bananashot-update").toFile() },
) {
    private val logger = KotlinLogging.logger {}
    private val running = AtomicBoolean(false)

    sealed interface Result {
        /** The user did not confirm the stop of the running export. Nothing changed. */
        data object Canceled : Result

        /** A download runs already. A second click has no effect. */
        data object AlreadyRunning : Result

        /** The download failed. The app stays open. Show [message] and keep "Manually download update". */
        data class DownloadFailed(val message: String) : Result

        /** The app did the close sequence and tried to start the setup. Now the process must exit. */
        data object Closing : Result
    }

    /** True while a download runs. Then "Update and restart" shows the progress, and the user cannot click it. */
    val isRunning: Boolean get() = running.get()

    /**
     * @param installerUrl [UpdateOptions.installerUrl].
     * @param exportRuns true when an export runs now.
     * @param confirmExportRestart asks the user: the running export starts again from the beginning
     *   after the update, and the queued exports continue (B-18). False cancels the update.
     * @param progress the bytes that arrived and the size of the file, if known.
     * @param closeSequence the normal close: save the open project and all changes that wait for a
     *   save, write the saved time and the export queue. After it, the app must not write anything,
     *   because the setup kills the processes of the install folder with no clean stop. If it fails,
     *   the update logs the failure and still starts the setup and exits.
     * @param exit ends the process.
     */
    fun run(
        installerUrl: String,
        exportRuns: () -> Boolean,
        confirmExportRestart: () -> Boolean,
        progress: (bytes: Long, totalBytes: Long?) -> Unit,
        closeSequence: () -> Unit,
        exit: () -> Unit,
    ): Result {
        if (!running.compareAndSet(false, true)) return Result.AlreadyRunning
        try {
            if (exportRuns() && !confirmExportRestart()) return Result.Canceled

            val folder = temporaryFolder()
            val setup = File(folder, UpdateOptions.SETUP_EXE_NAME)
            try {
                logger.info { "Downloading the setup from $installerUrl" }
                downloader.download(URI(installerUrl), setup, progress)
            } catch (failure: Exception) {
                logger.warn(failure) { "The download of the setup failed." }
                setup.delete()
                folder.delete()
                return Result.DownloadFailed(failure.message ?: failure.javaClass.simpleName)
            }

            try {
                closeSequence()
            } catch (failure: Exception) {
                // After a part of the close, the app cannot continue. Thus, start the setup and exit also here.
                logger.error(failure) { "The close sequence before the update failed. The update continues." }
            }
            // The last step. The setup kills this process when the user clicks "Update".
            try {
                launcher.start(setup)
                logger.info { "Started the setup $setup. The app exits." }
            } catch (failure: Exception) {
                logger.error(failure) { "The setup $setup did not start." }
            }
            exit()
            return Result.Closing
        } finally {
            running.set(false)
        }
    }
}
