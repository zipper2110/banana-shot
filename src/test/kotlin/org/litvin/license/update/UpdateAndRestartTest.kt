package org.litvin.license.update

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.litvin.license.LatestRelease
import org.litvin.license.VersionRules
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateAndRestartTest {
    @TempDir
    lateinit var dir: File

    private val latest = LatestRelease(
        version = "1.4.0",
        downloadUrl = "https://example.org/download",
        installerUrl = "https://example.org/BananaShot-win-Setup.exe",
    )

    @Test
    fun `a newer latest version shows both buttons with the URLs of the file`() {
        val options = UpdateOptions.of("1.3.0", latest)

        assertTrue(options.showsUpdateAndRestart)
        assertEquals(latest.installerUrl, options.installerUrl)
        assertEquals(latest.downloadUrl, options.downloadPageUrl)
    }

    @Test
    fun `when latest is the app version or older only Download update shows`() {
        assertFalse(UpdateOptions.of("1.4.0", latest).showsUpdateAndRestart)
        assertFalse(UpdateOptions.of("1.4.0-SNAPSHOT", latest).showsUpdateAndRestart)
        assertFalse(UpdateOptions.of("1.5.0", latest).showsUpdateAndRestart)
    }

    @Test
    fun `with no installerUrl the app uses the stable URL`() {
        val options = UpdateOptions.of("1.3.0", latest.copy(installerUrl = null))

        assertEquals(
            "https://github.com/zipper2110/banana-shot/releases/latest/download/BananaShot-win-Setup.exe",
            options.installerUrl,
        )
    }

    @Test
    fun `with no saved file both buttons show with the stable URL and the releases page`() {
        for (options in listOf(UpdateOptions.of("1.3.0", null as VersionRules?), UpdateOptions.of("1.3.0", VersionRules(null, emptyList())))) {
            assertTrue(options.showsUpdateAndRestart)
            assertEquals(UpdateOptions.STABLE_INSTALLER_URL, options.installerUrl)
            assertEquals(UpdateOptions.RELEASES_PAGE_URL, options.downloadPageUrl)
        }
    }

    @Test
    fun `the release script gives the setup EXE the name that the app downloads`() {
        val script = File("distribution/windows/Build-VelopackRelease.ps1").readText()

        assertTrue(script.contains("\$setupName = \"${UpdateOptions.SETUP_EXE_NAME}\""), "Build-VelopackRelease.ps1 must make ${UpdateOptions.SETUP_EXE_NAME}")
    }

    @Test
    fun `the update downloads the installer URL, does the close sequence, starts the setup as the last step, and exits`() {
        val steps = mutableListOf<String>()
        val update = UpdateAndRestart(
            downloader = { url, target, progress ->
                steps += "download $url"
                target.writeText("setup")
                progress(5, 5)
            },
            launcher = { setup -> steps += "start ${setup.name} (${setup.readText()})" },
            temporaryFolder = { dir },
        )

        val result = update.run(
            installerUrl = latest.installerUrl!!,
            exportRuns = { false },
            confirmExportRestart = { error("no question when no export runs") },
            progress = { bytes, total -> steps += "progress $bytes/$total" },
            closeSequence = { steps += "close" },
            exit = { steps += "exit" },
        )

        assertEquals(UpdateAndRestart.Result.Closing, result)
        assertEquals(
            listOf(
                "download ${latest.installerUrl}",
                "progress 5/5",
                "close",
                "start BananaShot-win-Setup.exe (setup)",
                "exit",
            ),
            steps,
        )
    }

    @Test
    fun `with a running export the app asks first and Cancel changes nothing`() {
        var downloaded = false
        var closed = false
        val update = UpdateAndRestart(
            downloader = { _, _, _ -> downloaded = true },
            launcher = { error("must not start") },
            temporaryFolder = { dir },
        )

        val result = update.run(latest.installerUrl!!, { true }, { false }, { _, _ -> }, { closed = true }, { error("must not exit") })

        assertEquals(UpdateAndRestart.Result.Canceled, result)
        assertFalse(downloaded)
        assertFalse(closed)
        assertFalse(update.isRunning)
    }

    @Test
    fun `with a running export a confirmed update continues`() {
        var asked = false
        val update = UpdateAndRestart({ _, target, _ -> target.writeText("setup") }, { }, { dir })

        val result = update.run(latest.installerUrl!!, { true }, { asked = true; true }, { _, _ -> }, { }, { })

        assertTrue(asked)
        assertEquals(UpdateAndRestart.Result.Closing, result)
    }

    @Test
    fun `a failed download deletes the partial file, keeps the app open, and gives the error`() {
        val folder = dir.resolve("download").apply { mkdirs() }
        var closed = false
        val update = UpdateAndRestart(
            downloader = { _, target, _ ->
                target.writeText("half")
                throw IOException("connection reset")
            },
            launcher = { error("must not start") },
            temporaryFolder = { folder },
        )

        val result = update.run(latest.installerUrl!!, { false }, { true }, { _, _ -> }, { closed = true }, { error("must not exit") })

        assertEquals(UpdateAndRestart.Result.DownloadFailed("connection reset"), result)
        assertFalse(closed)
        assertFalse(folder.resolve(UpdateOptions.SETUP_EXE_NAME).exists())
        assertFalse(update.isRunning)
    }

    @Test
    fun `a second click during the download has no effect`() {
        val downloadStarted = CountDownLatch(1)
        val finishDownload = CountDownLatch(1)
        val update = UpdateAndRestart(
            downloader = { _, target, _ ->
                downloadStarted.countDown()
                finishDownload.await(10, TimeUnit.SECONDS)
                target.writeText("setup")
            },
            launcher = { },
            temporaryFolder = { dir },
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<UpdateAndRestart.Result> {
                update.run(latest.installerUrl!!, { false }, { true }, { _, _ -> }, { }, { })
            }
            assertTrue(downloadStarted.await(10, TimeUnit.SECONDS))

            val second = update.run(latest.installerUrl!!, { false }, { true }, { _, _ -> }, { error("second close") }, { error("second exit") })

            assertEquals(UpdateAndRestart.Result.AlreadyRunning, second)
            finishDownload.countDown()
            assertEquals(UpdateAndRestart.Result.Closing, first.get(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a close sequence that fails goes to the log, and the app still starts the setup and exits`() {
        // B-26, check 4: the close stopped the executor of the download thread, and invokeAndWait was interrupted.
        val steps = mutableListOf<String>()
        val update = UpdateAndRestart({ _, target, _ -> target.writeText("setup") }, { steps += "start" }, { dir })

        val result = update.run(latest.installerUrl!!, { false }, { true }, { _, _ -> }, { throw InterruptedException() }, { steps += "exit" })

        assertIs<UpdateAndRestart.Result.Closing>(result)
        assertEquals(listOf("start", "exit"), steps)
    }

    @Test
    fun `a setup that does not start goes to the log and the app still exits`() {
        var exited = false
        val update = UpdateAndRestart({ _, target, _ -> target.writeText("setup") }, { throw IOException("not a program") }, { dir })

        val result = update.run(latest.installerUrl!!, { false }, { true }, { _, _ -> }, { }, { exited = true })

        assertIs<UpdateAndRestart.Result.Closing>(result)
        assertTrue(exited)
    }

    @Test
    fun `the HTTP download follows a redirect and reports the progress`() {
        val body = ByteArray(200_000) { (it % 251).toByte() }
        withServer(
            "/latest/download/setup.exe" to { exchange ->
                exchange.responseHeaders.add("Location", "/release-files/setup.exe")
                exchange.sendResponseHeaders(302, -1)
            },
            "/release-files/setup.exe" to { exchange ->
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            },
        ) { base ->
            val target = dir.resolve("setup.exe")
            var last = 0L to (null as Long?)

            HttpSetupDownloader().download(URI("$base/latest/download/setup.exe"), target) { bytes, total -> last = bytes to total }

            assertTrue(body.contentEquals(target.readBytes()))
            assertEquals(body.size.toLong() to body.size.toLong(), last)
        }
    }

    @Test
    fun `the HTTP download fails for a status that is not 200`() {
        withServer("/missing.exe" to { exchange -> exchange.sendResponseHeaders(404, -1) }) { base ->
            val failure = assertFailsWith<IOException> {
                HttpSetupDownloader().download(URI("$base/missing.exe"), dir.resolve("setup.exe")) { _, _ -> }
            }
            assertTrue(failure.message!!.contains("404"), failure.message)
        }
    }

    private fun withServer(
        vararg routes: Pair<String, (com.sun.net.httpserver.HttpExchange) -> Unit>,
        test: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        routes.forEach { (path, handler) -> server.createContext(path) { exchange ->
                try {
                    handler(exchange)
                } finally {
                    exchange.close()
                }
            } }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
