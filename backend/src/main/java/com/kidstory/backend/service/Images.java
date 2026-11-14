package com.kidstory.backend.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;

/** Görsel yardımcıları: referans görselleri sağlayıcının sınırına küçültür. */
public final class Images {

    private Images() {
    }

    static final int MAX_SOURCE_SIDE = 6000;
    static final int MIN_SOURCE_SIDE = 200;

    /**
     * Yöneticinin yüklediği örnek resim. Yalnızca JPEG ve PNG kabul edilir; boyut, resim belleğe açılmadan önce
     * başlıktan okunup sınırlanır (çok büyük resimler belleği doldurmasın). Resim uzun kenarı maxSide olacak şekilde
     * JPEG'e yeniden kodlanır; böylece gömülü bilgiler (konum, cihaz) silinir. Geçersizse IllegalArgumentException.
     */
    public static byte[] sampleJpeg(byte[] source, int maxSide) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Yalnızca JPEG ya da PNG resim yükleyin");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new IllegalArgumentException("Yalnızca JPEG ya da PNG resim yükleyin");
                }
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_SOURCE_SIDE || h > MAX_SOURCE_SIDE) {
                    throw new IllegalArgumentException("Resim çok büyük; kenarları en fazla " + MAX_SOURCE_SIDE + " piksel olabilir");
                }
                if (Math.min(w, h) < MIN_SOURCE_SIDE) {
                    throw new IllegalArgumentException("Resim çok küçük; kenarları en az " + MIN_SOURCE_SIDE + " piksel olmalı");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Resim okunamadı");
        }
        try {
            return fitJpeg(source, maxSide);
        } catch (UncheckedIOException e) {
            throw new IllegalArgumentException("Resim okunamadı");
        }
    }

    /** Görseli uzun kenarı en fazla maxSide olacak şekilde küçültüp JPEG'e çevirir. */
    public static byte[] fitJpeg(byte[] source, int maxSide) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(source));
            if (img == null) {
                throw new IllegalArgumentException("Görsel okunamadı");
            }
            double scale = Math.min(1.0, (double) maxSide / Math.max(img.getWidth(), img.getHeight()));
            int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, w, h, java.awt.Color.WHITE, null);
            g.dispose();
            return jpeg(out, 0.9f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
