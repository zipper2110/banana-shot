package org.litvin.license.online

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLContext

/**
 * A local HTTP/1.1 server for the tests. It sends the exact bytes that [respond] gives, so a test controls each
 * header (for example `Date` and `Age`). [respond] gives null to send nothing and keep the connection open (a
 * server that does not answer). With [sslContext], the server uses HTTPS.
 */
class RawHttpServer(
    sslContext: SSLContext? = null,
    private val respond: (RecordedRequest) -> ByteArray?,
) : AutoCloseable {
    data class RecordedRequest(val requestLine: String, val headers: Map<String, String>)

    private val socket: ServerSocket = sslContext?.serverSocketFactory?.createServerSocket(0, 50, InetAddress.getLoopbackAddress())
        ?: ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val open = CopyOnWriteArrayList<Socket>()

    /** The requests that arrived. A request with a failed TLS handshake is not in the list. */
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    val port: Int get() = socket.localPort

    init {
        Thread({
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                open += connection
                Thread({ serve(connection) }, "raw-http-server-connection").apply { isDaemon = true }.start()
            }
        }, "raw-http-server").apply { isDaemon = true }.start()
    }

    private fun serve(connection: Socket) {
        try {
            val input = connection.getInputStream()
            val lines = mutableListOf<String>()
            val line = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte < 0) return
                if (byte == '\n'.code) {
                    val text = line.toString().trimEnd('\r')
                    if (text.isEmpty()) break
                    lines += text
                    line.clear()
                } else {
                    line.append(byte.toChar())
                }
            }
            val request = RecordedRequest(
                lines.first(),
                lines.drop(1).associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() },
            )
            requests += request
            val response = respond(request) ?: return // Keep the connection open with no answer.
            connection.getOutputStream().apply {
                write(response)
                flush()
            }
            connection.close()
        } catch (_: IOException) {
            // A failed TLS handshake or a client that closed the connection.
        }
    }

    override fun close() {
        socket.close()
        open.forEach { runCatching { it.close() } }
    }

    companion object {
        /** A response with `Content-Length` and `Connection: close`. */
        fun response(status: String, headers: List<String> = emptyList(), body: ByteArray = ByteArray(0)): ByteArray {
            val head = buildString {
                append("HTTP/1.1 ").append(status).append("\r\n")
                headers.forEach { append(it).append("\r\n") }
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            return head.toByteArray(Charsets.ISO_8859_1) + body
        }
    }
}
