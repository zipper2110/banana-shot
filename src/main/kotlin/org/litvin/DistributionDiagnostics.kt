package org.litvin

import org.litvin.license.online.UpdateTrust
import org.litvin.media.mpv.LibMpv
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.TimeUnit

data class DiagnosticCheck(val name: String, val passed: Boolean, val detail: String)

object DistributionDiagnostics {
    fun run(): Int {
        val checks = collect()
        println("${AppInfo.displayName} distribution diagnostics")
        checks.forEach { check ->
            println("[${if (check.passed) "PASS" else "FAIL"}] ${check.name}: ${check.detail}")
        }
        return if (checks.all { it.passed }) 0 else 1
    }

    internal fun collect(layout: RuntimeLayout = ApplicationLayout.current()): List<DiagnosticCheck> {
        return listOf(
            architectureCheck(),
            DiagnosticCheck(
                "Java runtime",
                Runtime.version().feature() >= 17,
                System.getProperty("java.runtime.version").orEmpty(),
            ),
            fileCheck("libmpv", layout.mpvDirectory?.resolve(libmpvFileName())),
            nativeMpvCheck(),
            executableCheck("FFmpeg", layout.ffmpegExecutable),
            executableCheck("FFprobe", layout.ffprobeExecutable),
            writableDirectoryCheck(layout.appDataDirectory),
            systemTrustStoreCheck(),
            httpsCheck(),
        )
    }

    /** The build has native files for x64 on Windows and for Apple Silicon (arm64) on macOS (B-13). */
    internal fun architectureCheck(
        osName: String = System.getProperty("os.name").orEmpty(),
        arch: String = System.getProperty("os.arch").orEmpty(),
    ): DiagnosticCheck {
        val expected = if (isMac(osName)) setOf("aarch64", "arm64") else setOf("amd64", "x86_64")
        return DiagnosticCheck("Architecture", arch.lowercase() in expected, arch.ifBlank { "<unknown>" })
    }

    /** The libmpv file that JNA loads: `libmpv-2.dll` on Windows, `libmpv.dylib` (a link to `libmpv.2.dylib`) on macOS. */
    internal fun libmpvFileName(osName: String = System.getProperty("os.name").orEmpty()): String =
        if (isMac(osName)) "libmpv.dylib" else "libmpv-2.dll"

    private fun isMac(osName: String): Boolean = osName.startsWith("Mac", ignoreCase = true)

    /**
     * The host of the rules file of build-expiry-spec.md. The check reads only the host, not the file:
     * each HTTP status shows that the proxy, TLS, and the trust of the update requests work.
     */
    internal val UPDATE_HOST: URI = URI("https://raw.githubusercontent.com/")

    /**
     * The system key store of "Trust for the HTTPS connection": `Windows-ROOT` on Windows (it needs the module
     * `jdk.crypto.mscapi`) and `KeychainStore-ROOT` on macOS.
     */
    internal fun systemTrustStoreType(osName: String = System.getProperty("os.name").orEmpty()): String =
        if (isMac(osName)) "KeychainStore-ROOT" else "Windows-ROOT"

    internal fun systemTrustStoreCheck(
        type: String = systemTrustStoreType(),
        load: () -> KeyStore = { KeyStore.getInstance(type).apply { load(null, null) } },
    ): DiagnosticCheck = try {
        val store = load()
        DiagnosticCheck("System trust store", store.size() > 0, "$type, ${store.size()} certificates")
    } catch (t: Throwable) {
        DiagnosticCheck("System trust store", false, "$type (${t.javaClass.simpleName}: ${t.message})")
    }

    /** An HTTPS request to [uri]. Each HTTP status passes. A failed connection or a TLS error fails. */
    internal fun httpsCheck(
        uri: URI = UPDATE_HOST,
        send: (URI) -> Int = ::sendHeadRequest,
    ): DiagnosticCheck = try {
        DiagnosticCheck("HTTPS connection", true, "$uri (HTTP ${send(uri)})")
    } catch (t: Throwable) {
        DiagnosticCheck("HTTPS connection", false, "$uri (${t.javaClass.simpleName}: ${t.message})")
    }

    private fun sendHeadRequest(uri: URI): Int {
        // main sets java.net.useSystemProxies before this check, so the request uses the proxy of Windows.
        // The trust is the same as for the rules file: the bundled store and Windows-ROOT (E5-S4).
        val client = UpdateTrust.production.client(HttpClient.Redirect.NEVER, Duration.ofSeconds(10))
        val request = HttpRequest.newBuilder(uri)
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(10))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun fileCheck(name: String, file: File?): DiagnosticCheck = DiagnosticCheck(
        name,
        file?.isFile == true,
        file?.absolutePath ?: "not configured",
    )

    private fun nativeMpvCheck(): DiagnosticCheck {
        return try {
            val version = LibMpv.instance().mpv_client_api_version().toLong()
            DiagnosticCheck("libmpv load", true, "client API ${version shr 16}.${version and 0xffff}")
        } catch (t: Throwable) {
            DiagnosticCheck("libmpv load", false, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun executableCheck(name: String, executable: String): DiagnosticCheck {
        return try {
            val process = ProcessBuilder(executable, "-version")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val firstLine = process.inputStream.bufferedReader().use { it.readLine() }.orEmpty()
            val passed = finished && process.exitValue() == 0
            DiagnosticCheck(
                name,
                passed,
                executable + firstLine.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
            )
        } catch (t: Throwable) {
            DiagnosticCheck(name, false, "$executable (${t.javaClass.simpleName}: ${t.message})")
        }
    }

    private fun writableDirectoryCheck(directory: File): DiagnosticCheck {
        return try {
            if (!directory.exists()) directory.mkdirs()
            val probe = File.createTempFile("write-probe-", ".tmp", directory)
            probe.delete()
            DiagnosticCheck("Application data", true, directory.absolutePath)
        } catch (t: Throwable) {
            DiagnosticCheck(
                "Application data",
                false,
                "${directory.absolutePath} (${t.javaClass.simpleName}: ${t.message})",
            )
        }
    }
}
