package org.litvin.ui.commons

import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The photo of the app author in a circle. The personal requests of the author show it in place of the message icon.
 * The source is a head crop of design/author/photo.jpg (see design/author/README.md).
 */
internal object AuthorPortrait {
    /** The round photo, or null when the app cannot read the resource. */
    val image: BufferedImage? by lazy { runCatching { load() }.getOrNull() }

    private fun load(): BufferedImage? {
        val photo = AuthorPortrait::class.java.getResourceAsStream("/author/portrait.jpg")?.use(ImageIO::read) ?: return null
        val size = minOf(photo.width, photo.height)
        val round = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = round.createGraphics()
        try {
            // A smooth circle mask, then the photo only inside it.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.fill(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))
            g.composite = AlphaComposite.SrcIn
            g.drawImage(photo, (size - photo.width) / 2, (size - photo.height) / 2, null)
        } finally {
            g.dispose()
        }
        return round
    }
}
