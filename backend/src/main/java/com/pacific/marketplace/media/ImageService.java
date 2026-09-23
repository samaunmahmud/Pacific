package com.pacific.marketplace.media;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.security.AttemptLimiter;
import com.pacific.marketplace.service.SellerService;
import com.pacific.marketplace.web.ApiException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Product photo uploads. The uploaded file is never stored as sent: it is decoded and saved again as a fresh JPEG
 * (or PNG when it has transparency), scaled down to app.uploads.max-dimension. That proves it really is an image,
 * keeps pages fast, and drops everything else the file carried, such as the location a phone photo was taken at.
 */
@Service
public class ImageService {

    public static final String URL_PREFIX = "/api/images/";
    private static final Pattern NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png)$");

    /** Refuse pictures with more pixels than this before decoding them (a tiny file can claim to be enormous). */
    private static final long MAX_SOURCE_PIXELS = 100_000_000L;
    private static final int MAX_SOURCE_SIDE = 20_000;
    private static final float JPEG_QUALITY = 0.86f;

    private final ImageStore store;
    private final SellerService sellers;
    private final long maxBytes;
    private final int maxDimension;
    private final AttemptLimiter uploads;
    /** Decoding a large photo takes a lot of memory for a moment, so only a couple are processed at once. */
    private final Semaphore processing = new Semaphore(2);

    public ImageService(ImageStore store, SellerService sellers, AppProperties props) {
        this.store = store;
        this.sellers = sellers;
        this.maxBytes = props.uploads().maxBytes();
        this.maxDimension = props.uploads().maxDimension();
        this.uploads = new AttemptLimiter(Clock.systemUTC(), props.uploads().hourlyLimit(), Duration.ofHours(1),
                "You've uploaded a lot of photos in the last hour.");
    }

    public record Uploaded(String url, int width, int height) {
    }

    record Processed(byte[] bytes, String extension, int width, int height) {
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Admins and approved sellers can upload; anyone else is told why not. */
    public Uploaded upload(Long userId, Role role, byte[] bytes) {
        if (role != Role.ADMIN) sellers.approved(userId);
        String key = role + ":" + userId;
        uploads.check(key);
        Processed p = process(bytes);
        uploads.hit(key);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + p.extension();
        store.save(name, p.bytes());
        return new Uploaded(URL_PREFIX + name, p.width(), p.height());
    }

    public Optional<byte[]> load(String name) {
        return isValidName(name) ? store.load(name) : Optional.empty();
    }

    Processed process(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.badRequest("Choose a photo to upload.");
        if (bytes.length > maxBytes) {
            throw ApiException.badRequest("That photo is too large. Photos can be up to " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        if (!looksLikeSupportedImage(bytes)) {
            throw ApiException.badRequest("That file isn't a photo we can use. Upload a JPEG, PNG or GIF.");
        }
        try {
            if (!processing.tryAcquire(20, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The shop is busy processing photos. Please try again.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The shop is busy processing photos. Please try again.");
        }
        try {
            BufferedImage image = decode(bytes);
            image = scaleDown(image, maxDimension);
            image = applyOrientation(image, jpegOrientation(bytes));
            boolean transparent = hasTransparency(image);
            return new Processed(transparent ? encodePng(image) : encodeJpeg(image), transparent ? "png" : "jpg",
                    image.getWidth(), image.getHeight());
        } finally {
            processing.release();
        }
    }

    /** Checks the file's first bytes, whatever its name or declared type says. */
    private static boolean looksLikeSupportedImage(byte[] b) {
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean gif = b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
        return jpeg || png || gif;
    }

    private BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw unreadable();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1) throw unreadable();
                if (w > MAX_SOURCE_SIDE || h > MAX_SOURCE_SIDE || (long) w * h > MAX_SOURCE_PIXELS) {
                    throw ApiException.badRequest("That photo is too big (" + w + " × " + h + " pixels). Please use a smaller one.");
                }
                // A very large photo is read at a reduced size straight away (keeping at least twice the size it
                // will be saved at, so the final scaling still looks good), which keeps memory use modest.
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(w, h) / (maxDimension * 2));
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw unreadable();
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) throw api;
            throw unreadable();
        }
    }

    private static ApiException unreadable() {
        return ApiException.badRequest("That photo couldn't be read. It may be damaged; try saving it again as a JPEG or PNG.");
    }

    /** Halves the picture until it's close, then does one smooth final step: sharper than a single big jump. */
    private static BufferedImage scaleDown(BufferedImage src, int max) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage current = toArgb(src);
        if (Math.max(w, h) <= max) return current;
        double ratio = (double) max / Math.max(w, h);
        int targetW = Math.max(1, (int) Math.round(w * ratio));
        int targetH = Math.max(1, (int) Math.round(h * ratio));
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2;
            h /= 2;
            current = resize(current, w, h);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static boolean hasTransparency(BufferedImage img) {
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        for (int p : px) {
            if ((p >>> 24) != 0xFF) return true;
        }
        return false;
    }

    private static byte[] encodeJpeg(BufferedImage argb) {
        BufferedImage rgb = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(argb, 0, 0, null);
        } finally {
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage argb) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(argb, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't encode PNG", e);
        }
        return out.toByteArray();
    }

    /**
     * Phones save photos sideways and note in the file which way up they go (EXIF orientation, 1-8). Re-saving drops
     * that note, so the turn is applied to the pixels instead. Returns 1 (as is) when the file doesn't say.
     */
    static int jpegOrientation(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return 1;
        int i = 2;
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) break; // image data starts: no more metadata
            int len = u16(b, i + 2, false);
            if (len < 2) break;
            int start = i + 4;
            if (marker == 0xE1 && start + 14 <= b.length && b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i'
                    && b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0) {
                return exifOrientation(b, start + 6, Math.min(b.length, i + 2 + len));
            }
            i += 2 + len;
        }
        return 1;
    }

    private static int exifOrientation(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) return 1;
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) return 1;
        long ifd = tiff + u32(b, tiff + 4, little);
        if (ifd + 2 > end) return 1;
        int count = u16(b, (int) ifd, little);
        for (int n = 0; n < count; n++) {
            int entry = (int) ifd + 2 + n * 12;
            if (entry + 12 > end) return 1;
            if (u16(b, entry, little) == 0x0112) {
                int v = u16(b, entry + 8, little);
                return v >= 1 && v <= 8 ? v : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean little) {
        int a = b[at] & 0xFF;
        int c = b[at + 1] & 0xFF;
        return little ? a | (c << 8) : (a << 8) | c;
    }

    private static long u32(byte[] b, int at, boolean little) {
        long v = 0;
        for (int k = 0; k < 4; k++) {
            int idx = little ? at + 3 - k : at + k;
            v = (v << 8) | (b[idx] & 0xFF);
        }
        return v;
    }

    /** Turns/flips the picture so it shows the right way up for EXIF orientation 2-8. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int dw = swap ? h : w;
        int dh = swap ? w : h;
        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { dx = w - 1 - x; dy = y; }
                    case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }
                    case 4 -> { dx = x; dy = h - 1 - y; }
                    case 5 -> { dx = y; dy = x; }
                    case 6 -> { dx = h - 1 - y; dy = x; }
                    case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }
                    default -> { dx = y; dy = w - 1 - x; }
                }
                out[dy * dw + dx] = in[y * w + x];
            }
        }
        BufferedImage dst = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        dst.setRGB(0, 0, dw, dh, out, 0, dw);
        return dst;
    }

    @Scheduled(fixedDelay = 300_000)
    void purge() {
        uploads.purge();
    }
}
