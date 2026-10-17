package org.litvin.share

import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Writes the thumbnail PNG. YouTube accepts a thumbnail of 2 MB or less. */
object ThumbnailPng {
    const val MAX_BYTES = 2L * 1024 * 1024

    /** The number of softer frames that [write] tries after the first PNG. */
    const val SOFTER_TRIES = 3

    /**
     * Writes the image of [render] to [file] with the strongest PNG compression, and returns the size in bytes.
     * A frame with much fine detail (grass, gravel, sensor noise) can make a larger PNG. Then the app renders the image
     * again with a softer frame ([render] gets 1, 2, 3), until the file is not larger than [MAX_BYTES].
     * The texts stay sharp. The file is replaced in one step, so that a failed save does not damage the old file.
     */
    fun write(file: File, render: (softness: Int) -> BufferedImage): Long {
        file.absoluteFile.parentFile?.mkdirs()
        val temp = File(file.absoluteFile.parentFile, ".${file.name}.tmp")
        try {
            var bytes = writePng(render(0), temp)
            var softness = 0
            while (bytes > MAX_BYTES && softness < SOFTER_TRIES) {
                softness++
                bytes = writePng(render(softness), temp)
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            return bytes
        } finally {
            temp.delete()
        }
    }

    private fun writePng(image: BufferedImage, file: File): Long {
        // The image output stream does not make an existing file shorter.
        file.delete()
        val writer = ImageIO.getImageWritersByFormatName("png").next()
        try {
            ImageIO.createImageOutputStream(file).use { output ->
                writer.output = output
                val param = writer.defaultWriteParam
                if (param.canWriteCompressed()) {
                    param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    // 0.0 is the strongest compression of the PNG writer.
                    param.compressionQuality = 0f
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return file.length()
    }

    /** A light blur. Fine detail makes a PNG large. */
    fun soften(image: BufferedImage): BufferedImage {
        val center = 0.6f
        val side = (1f - center) / 8f
        val kernel = Kernel(3, 3, floatArrayOf(side, side, side, side, center, side, side, side, side))
        val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(image, result)
        return result
    }
}
