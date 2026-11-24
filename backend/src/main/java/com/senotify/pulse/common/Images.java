package com.senotify.pulse.common;

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
 * Normalizes uploaded images to JPEG. Re-encoding means only real images are stored, metadata
 * (EXIF, including GPS location) is stripped, and sizes are predictable. Dimensions are read from
 * the header before decoding, so a small file declaring a huge canvas (a decompression bomb) is
 * rejected without allocating it.
 */
public final class Images {

    public record Jpeg(byte[] bytes, int width, int height) {
    }

    static final int MAX_DIMENSION = 6000;
    static final int MIN_DIMENSION = 32;
    private static final float QUALITY = 0.86f;

    private Images() {
    }

    /** Centre-cropped square of {@code size} px (profile photos). */
    public static Jpeg square(byte[] upload, int size) {
        BufferedImage source = decode(upload);
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage crop = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        return encode(resize(crop, size, size));
    }

    /** Keeps the aspect ratio, shrinking so the longest side is at most {@code maxSide} px (post photos). */
    public static Jpeg fit(byte[] upload, int maxSide) {
        BufferedImage source = decode(upload);
        double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return encode(resize(source, width, height));
    }

    private static BufferedImage decode(byte[] upload) {
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
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("Could not read that image");
        }
    }

    /** Halves repeatedly before the final step, since one big bicubic downscale aliases badly. */
    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage current = source;
        while (current.getWidth() >= width * 2 && current.getHeight() >= height * 2) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, width, height);
    }

    /** Draws onto an opaque white canvas: JPEG has no transparency. */
    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static Jpeg encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(QUALITY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return new Jpeg(bytes.toByteArray(), image.getWidth(), image.getHeight());
    }
}
