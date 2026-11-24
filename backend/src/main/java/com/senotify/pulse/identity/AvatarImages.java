package com.senotify.pulse.identity;

import com.senotify.pulse.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Turns an uploaded image into a square 400x400 JPEG. Re-encoding means only real images are stored,
 * metadata (EXIF, including GPS location) is stripped, and every avatar has a predictable size.
 * Dimensions are checked from the header before decoding, so a tiny file that declares a huge
 * canvas (a decompression bomb) is rejected without allocating it.
 */
final class AvatarImages {

    static final int SIZE = 400;
    static final int MAX_DIMENSION = 6000;
    static final int MIN_DIMENSION = 32;
    private static final float QUALITY = 0.88f;

    private AvatarImages() {
    }

    static byte[] normalize(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw ApiException.badRequest("Unsupported image. Use JPEG, PNG or GIF.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw ApiException.badRequest("Image is too large (max " + MAX_DIMENSION + " px per side)");
                }
                if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
                    throw ApiException.badRequest("Image is too small (min " + MIN_DIMENSION + " px per side)");
                }
                return encodeJpeg(squareCrop(reader.read(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.badRequest("Could not read that image");
        }
    }

    /** Centre crop to a square, scaled to SIZE, flattened onto white (JPEG has no transparency). */
    private static BufferedImage squareCrop(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;

        BufferedImage current = source.getSubimage(x, y, side, side);
        // Halve repeatedly before the final step: a single big bicubic downscale aliases badly.
        while (current.getWidth() >= SIZE * 2) {
            current = scale(current, current.getWidth() / 2);
        }
        return scale(current, SIZE);
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(QUALITY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
