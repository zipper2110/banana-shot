package org.litvin.share

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThumbnailLogoTest {
    private val folder = Files.createTempDirectory("thumbnail-logo-test").toFile().apply { deleteOnExit() }

    private fun image(w: Int, h: Int, type: Int = BufferedImage.TYPE_INT_RGB) = BufferedImage(w, h, type).apply {
        val g2 = createGraphics()
        g2.color = Color(0xE3262E)
        g2.fillRect(0, 0, w / 2, h)
        g2.dispose()
    }

    private fun file(name: String, image: BufferedImage, format: String): File =
        File(folder, name).also { assertTrue(ImageIO.write(image, format, it), "ImageIO cannot write $format") }

    @Test
    fun theAppReadsTheImageFormatsOfJava() {
        for ((name, format) in listOf("logo.jpg" to "jpg", "logo.gif" to "gif", "logo.bmp" to "bmp", "logo.tiff" to "tiff", "source.png" to "png")) {
            val stored = ThumbnailLogos.store(file(name, image(300, 120), format), File(folder, "store"), ffmpeg = null)

            val logo = assertNotNull(ThumbnailLogos.load(stored), name)
            assertEquals(300, logo.image.width, name)
            assertEquals(120, logo.image.height, name)
            assertEquals(BufferedImage.TYPE_INT_ARGB, logo.image.type, name)
        }
    }

    @Test
    fun theAppDrawsAnSvgLogoWithItsAspect() {
        val svg = File(folder, "logo.svg").apply {
            writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" width="240" height="120" viewBox="0 0 240 120">""" +
                    """<circle cx="60" cy="60" r="50" fill="#FFD400"/></svg>""",
            )
        }

        val stored = ThumbnailLogos.store(svg, File(folder, "svg"), ffmpeg = null)

        val logo = assertNotNull(ThumbnailLogos.load(stored))
        assertEquals(ThumbnailLogos.MAX_SIDE, logo.image.width)
        assertEquals(ThumbnailLogos.MAX_SIDE / 2, logo.image.height)
        // The middle of the circle is yellow, and the right side is transparent.
        assertEquals(0xFFFFD400.toInt(), logo.image.getRGB(256, 256))
        assertEquals(0, logo.image.getRGB(1000, 256) ushr 24)
    }

    @Test
    fun aLargeLogoBecomesSmaller() {
        val fitted = ThumbnailLogos.fit(image(4000, 1000))

        assertEquals(ThumbnailLogos.MAX_SIDE, fitted.width)
        assertEquals(256, fitted.height)
    }

    @Test
    fun theIdChangesWithTheImage() {
        val first = ThumbnailLogos.load(ThumbnailLogos.store(file("a.png", image(100, 50), "png"), File(folder, "a"), null))
        val same = ThumbnailLogos.load(ThumbnailLogos.store(file("b.png", image(100, 50), "png"), File(folder, "b"), null))
        val other = ThumbnailLogos.load(ThumbnailLogos.store(file("c.png", image(100, 60), "png"), File(folder, "c"), null))

        assertEquals(first, same)
        assertNotEquals(first, other)
    }

    @Test
    fun aFileThatIsNotAnImageGivesAnError() {
        val text = File(folder, "logo.png").apply { writeText("not an image") }

        assertFailsWith<IOException> { ThumbnailLogos.store(text, File(folder, "bad"), ffmpeg = null) }
        assertFailsWith<IOException> { ThumbnailLogos.store(File(folder, "missing.png"), File(folder, "bad"), ffmpeg = null) }
        assertNull(ThumbnailLogos.load(text))
        assertNull(ThumbnailLogos.load(null))
    }
}
