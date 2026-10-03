package org.litvin.license.online

import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Self-signed server certificates for the HTTPS tests. The JDK `keytool` makes them, because the JDK has no public
 * API to make a certificate. The tests use their own key stores and never change the real Windows store.
 */
class TestCertificates private constructor(private val folder: File) {
    /** A server key and a self-signed certificate for [subjectAltName], for example `dns:localhost,ip:127.0.0.1`. */
    inner class ServerKey(name: String, subjectAltName: String) {
        private val file = folder.resolve("$name.p12")
        private val store: KeyStore

        init {
            keytool(
                "-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=$name", "-ext", "SAN=$subjectAltName", "-validity", "2",
                "-keystore", file.absolutePath, "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD,
            )
            store = KeyStore.getInstance(file, PASSWORD.toCharArray())
        }

        val certificate: Certificate get() = store.getCertificate("server")

        /** The context of a server that shows this certificate. */
        fun serverContext(): SSLContext {
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keys.init(store, PASSWORD.toCharArray())
            return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        }
    }

    private fun keytool(vararg arguments: String) {
        val executable = File(System.getProperty("java.home"), "bin/keytool").absolutePath
        val process = ProcessBuilder(listOf(executable) + arguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "keytool failed: $output" }
    }

    companion object {
        private const val PASSWORD = "changeit"

        fun create(): TestCertificates =
            TestCertificates(Files.createTempDirectory("bananashot-test-certificates").toFile().apply { deleteOnExit() })

        /** A trust store with only [certificates]. */
        fun trustStore(vararg certificates: Certificate): KeyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certificates.forEachIndexed { index, certificate -> setCertificateEntry("trusted-$index", certificate) }
            }
    }
}
