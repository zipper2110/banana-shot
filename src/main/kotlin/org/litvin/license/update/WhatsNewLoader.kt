package org.litvin.license.update

import io.github.oshai.kotlinlogging.KotlinLogging
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import org.litvin.license.online.UpdateTrust
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.IdentityHashMap
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** An image of the page in a local file, with its size in pixels. */
data class LocalImage(val uri: URI, val width: Int, val height: Int)

/**
 * Loads the Markdown page of the "What's new" popup (B-54) and its images, and converts the page to HTML for
 * `JEditorPane`. The images go to local files, so the HTML has their sizes and the app reads them with its own HTTPS
 * trust. Only HTTPS addresses load. The load blocks, so call it on a thread that is not the EDT.
 */
class WhatsNewLoader(
    /** Reads [URI] and gives at most [Int] + 1 bytes, or null for each answer that is not HTTP 200. */
    private val fetch: (URI, Int) -> ByteArray?,
    /** The folder for the image files. */
    private val imageFolder: () -> Path = ::temporaryFolder,
) {
    /** The HTML of the page, or null when the page does not load. A missing image does not stop the page. */
    fun load(markdownUrl: String, maxImageWidth: Int): String? = try {
        val base = URI(markdownUrl.trim())
        if (!base.isHttps()) {
            logger.warn { "What's new: the page address is not HTTPS: $markdownUrl" }
            null
        } else {
            val bytes = fetch(base, MAX_PAGE_BYTES)
            when {
                bytes == null -> null.also { logger.warn { "What's new: the page did not load: $markdownUrl" } }
                bytes.size > MAX_PAGE_BYTES -> null.also { logger.warn { "What's new: the page is larger than $MAX_PAGE_BYTES bytes" } }
                else -> {
                    var count = 0
                    WhatsNewHtml.render(bytes.toString(Charsets.UTF_8), base, maxImageWidth) { uri ->
                        if (count >= MAX_IMAGES) null else loadImage(uri, count++)
                    }
                }
            }
        }
    } catch (failure: Exception) {
        logger.warn(failure) { "What's new: the page did not load: $markdownUrl" }
        null
    }

    private fun loadImage(uri: URI, index: Int): LocalImage? = try {
        val bytes = fetch(uri, MAX_IMAGE_BYTES)
        val image = bytes?.takeIf { it.size <= MAX_IMAGE_BYTES }?.let { ImageIO.read(ByteArrayInputStream(it)) }
        if (bytes == null || image == null) {
            logger.warn { "What's new: the image did not load or is not PNG, JPEG, GIF, or BMP: $uri" }
            null
        } else {
            val file = imageFolder().resolve("image-$index")
            Files.write(file, bytes)
            file.toFile().deleteOnExit()
            LocalImage(file.toUri(), image.width, image.height)
        }
    } catch (failure: Exception) {
        logger.warn(failure) { "What's new: the image did not load: $uri" }
        null
    }

    companion object {
        const val MAX_PAGE_BYTES = 256 * 1024
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        const val MAX_IMAGES = 30
        val TIMEOUT: Duration = Duration.ofSeconds(10)

        private val logger = KotlinLogging.logger {}

        /** The production loader: the HTTPS trust of the update server, and redirects to HTTPS only. */
        fun production(): WhatsNewLoader {
            val client by lazy { UpdateTrust.production.client(HttpClient.Redirect.NORMAL, TIMEOUT) }
            return WhatsNewLoader(fetch = { uri, limit -> httpGet(client, uri, limit) })
        }

        private fun httpGet(client: HttpClient, uri: URI, limit: Int): ByteArray? {
            val request = HttpRequest.newBuilder(uri).GET().timeout(TIMEOUT).build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                if (response.statusCode() != 200) return null
                return body.readNBytes(limit + 1)
            }
        }

        private fun temporaryFolder(): Path = folder

        private val folder: Path by lazy {
            Files.createTempDirectory("bananashot-whats-new").also { it.toFile().deleteOnExit() }
        }
    }
}

/** Converts the Markdown of the "What's new" page to HTML. HTML in the Markdown shows as text. */
object WhatsNewHtml {
    private val extensions = listOf(TablesExtension.create())
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    /**
     * [image] gives the local file of an image address, or null. An image with no file does not show. Relative
     * addresses use [base]. A link that is not HTTP, HTTPS, or mailto has no address.
     */
    fun render(markdown: String, base: URI, maxImageWidth: Int, image: (URI) -> LocalImage?): String {
        val document = parser.parse(markdown)
        val images = IdentityHashMap<Node, LocalImage>()
        val links = IdentityHashMap<Node, String>()
        val missing = mutableListOf<Node>()
        document.accept(object : AbstractVisitor() {
            override fun visit(node: Image) {
                val local = resolve(base, node.destination)?.takeIf { it.isHttps() }?.let(image)
                if (local == null) missing += node else images[node] = local
            }

            override fun visit(node: Link) {
                resolve(base, node.destination)?.takeIf { it.scheme?.lowercase() in LINK_SCHEMES }?.let { links[node] = it.toString() }
                visitChildren(node)
            }
        })
        missing.forEach(Node::unlink)

        val renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory {
                AttributeProvider { node, _, attributes ->
                    images[node]?.let { local ->
                        val (width, height) = fit(local, maxImageWidth)
                        attributes["src"] = local.uri.toString()
                        attributes["width"] = width.toString()
                        attributes["height"] = height.toString()
                    }
                    if (node is Link) {
                        val href = links[node]
                        if (href == null) attributes.remove("href") else attributes["href"] = href
                    }
                }
            }
            .build()
        return renderer.render(document)
    }

    /** The size on the screen: an image wider than [maxWidth] gets smaller, with the same aspect ratio. */
    fun fit(image: LocalImage, maxWidth: Int): Pair<Int, Int> {
        if (image.width <= maxWidth || image.width <= 0) return image.width to image.height
        return maxWidth to (image.height.toDouble() * maxWidth / image.width).roundToInt().coerceAtLeast(1)
    }

    private val LINK_SCHEMES = setOf("http", "https", "mailto")

    private fun resolve(base: URI, destination: String): URI? = try {
        base.resolve(URI(destination.trim()))
    } catch (_: Exception) {
        null
    }
}

private fun URI.isHttps() = scheme.equals("https", ignoreCase = true) && !host.isNullOrEmpty()
