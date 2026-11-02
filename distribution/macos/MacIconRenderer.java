import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders the app icon SVG into a macOS iconset folder (B-13). iconutil makes the .icns file from the folder.
 * A macOS app icon leaves a margin around the tile: on the 1024 grid of Apple, the tile is 824 pixels wide.
 * Usage: java -cp jsvg.jar MacIconRenderer.java <svg> <iconset-folder>
 */
public class MacIconRenderer {
    private static final int[] SIZES = {16, 32, 128, 256, 512};
    private static final double TILE_SHARE = 824.0 / 1024.0;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: MacIconRenderer <svg> <iconset-folder>");
        }
        SVGDocument document = new SVGLoader().load(Path.of(args[0]).toUri().toURL());
        if (document == null) {
            throw new IOException("Cannot parse SVG: " + args[0]);
        }
        Path folder = Path.of(args[1]);
        Files.createDirectories(folder);
        for (int size : SIZES) {
            write(render(document, size), folder.resolve("icon_" + size + "x" + size + ".png"));
            write(render(document, size * 2), folder.resolve("icon_" + size + "x" + size + "@2x.png"));
        }
    }

    private static BufferedImage render(SVGDocument document, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            float tile = (float) (size * TILE_SHARE);
            float offset = (size - tile) / 2f;
            document.render(null, graphics, new ViewBox(offset, offset, tile, tile));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void write(BufferedImage image, Path file) throws IOException {
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("No PNG writer");
        }
    }
}
