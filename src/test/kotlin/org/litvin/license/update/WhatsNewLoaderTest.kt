package org.litvin.license.update

import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-54: the Markdown page of the "What's new" popup. */
class WhatsNewLoaderTest {
    @TempDir
    lateinit var folder: Path

    private val pageUrl = "https://example.invalid/changelog/1.4.0/whats-new.md"
    private val files = mutableMapOf<URI, ByteArray>()
    private val requests = mutableListOf<URI>()

    private val loader by lazy {
        WhatsNewLoader(fetch = { uri, _ -> requests += uri; files[uri] }, imageFolder = { folder })
    }

    private fun page(markdown: String) {
        files[URI(pageUrl)] = markdown.toByteArray()
    }

    private fun png(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    @Test
    fun `the page gives HTML with headings, lists, and links that resolve against the page address`() {
        page(
            """
            # Smarter replays

            The first replay **zooms in**.

            - Export no longer stops
            - The score is correct

            [Guide](../../guides/replays/) and [mail](mailto:help@example.invalid)
            """.trimIndent(),
        )

        val html = assertNotNull(loader.load(pageUrl, 500))

        assertTrue(html.contains("<h1>Smarter replays</h1>"), html)
        assertTrue(html.contains("<strong>zooms in</strong>"), html)
        assertTrue(html.contains("<li>Export no longer stops</li>"), html)
        assertTrue(html.contains("href=\"https://example.invalid/guides/replays/\""), html)
        assertTrue(html.contains("href=\"mailto:help@example.invalid\""), html)
    }

    @Test
    fun `an image goes to a local file, and a wide image gets the width of the popup`() {
        files[URI("https://example.invalid/changelog/1.4.0/zoom.png")] = png(1200, 600)
        files[URI("https://example.invalid/icon.png")] = png(32, 32)
        page("![Zoom](zoom.png)\n\n![Icon](https://example.invalid/icon.png)")

        val html = assertNotNull(loader.load(pageUrl, 500))

        assertTrue(html.contains("width=\"500\" height=\"250\""), html)
        assertTrue(html.contains("width=\"32\" height=\"32\""), html)
        assertTrue(html.contains("src=\"${folder.resolve("image-0").toUri()}\""), html)
        assertFalse(html.contains("example.invalid/changelog/1.4.0/zoom.png"), html)
    }

    @Test
    fun `an image that does not load, is not HTTPS, or is not an image does not show, and the page stays`() {
        files[URI("https://example.invalid/text.png")] = "not an image".toByteArray()
        files[URI("http://example.invalid/plain.png")] = png(10, 10)
        page("Text\n\n![Missing](missing.png)\n\n![Text](https://example.invalid/text.png)\n\n![Plain](http://example.invalid/plain.png)")

        val html = assertNotNull(loader.load(pageUrl, 500))

        assertTrue(html.contains("<p>Text</p>"), html)
        assertFalse(html.contains("<img"), html)
        assertFalse(URI("http://example.invalid/plain.png") in requests)
    }

    @Test
    fun `HTML in the Markdown shows as text, and a script link has no address`() {
        page("<object classid=\"javax.swing.JButton\"></object>\n\nA <b>bold</b> word and [a link](javascript:alert(1))")

        val html = assertNotNull(loader.load(pageUrl, 500))

        assertFalse(html.contains("<object"), html)
        assertFalse(html.contains("<b>"), html)
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt;"), html)
        assertFalse(html.contains("javascript"), html)
        assertTrue(html.contains("<a rel=\"nofollow\">a link</a>"), html)
    }

    @Test
    fun `a page that is missing, too large, or not HTTPS gives no HTML`() {
        assertNull(loader.load(pageUrl, 500))

        files[URI(pageUrl)] = ByteArray(WhatsNewLoader.MAX_PAGE_BYTES + 1) { 'a'.code.toByte() }
        assertNull(loader.load(pageUrl, 500))

        files[URI("http://example.invalid/whats-new.md")] = "# Title".toByteArray()
        assertNull(loader.load("http://example.invalid/whats-new.md", 500))
        assertNull(loader.load("not a url", 500))
    }

    @Test
    fun `a table shows as an HTML table`() {
        page("| Before | After |\n|---|---|\n| 10 s | 5 s |")

        val html = assertNotNull(loader.load(pageUrl, 500))

        assertTrue(html.contains("<table>"), html)
        assertTrue(html.contains("<td>5 s</td>"), html)
    }

    @Test
    fun `fit keeps a narrow image and makes a wide image smaller with the same aspect ratio`() {
        assertEquals(400 to 300, WhatsNewHtml.fit(LocalImage(URI("file:/a"), 400, 300), 500))
        assertEquals(500 to 375, WhatsNewHtml.fit(LocalImage(URI("file:/a"), 1000, 750), 500))
    }
}
