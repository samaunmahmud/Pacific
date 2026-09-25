package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sellers and admins upload product photos; the shop re-saves them and serves them to everyone. */
class ProductPhotoTest extends IntegrationTest {

    private String sellerToken(boolean approve) throws Exception {
        String token = registerCustomer();
        MvcResult r = mvc.perform(bearer(post("/api/seller/apply"), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("storeName", "Photo Store " + token.hashCode(), "description", "Pictures")))).andReturn();
        long id = read(r).get("id").asLong();
        if (approve) {
            mvc.perform(bearer(patch("/api/admin/sellers/" + id + "/status"), adminToken())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "APPROVED"))))
                    .andExpect(status().isOk());
        }
        return token;
    }

    private MockHttpServletRequestBuilder upload(byte[] bytes, String filename, String token) {
        MockHttpServletRequestBuilder b = multipart("/api/images").file(new MockMultipartFile("file", filename, "image/jpeg", bytes));
        return token == null ? b : bearer(b, token);
    }

    private JsonNode uploadOk(byte[] bytes, String token) throws Exception {
        return read(mvc.perform(upload(bytes, "photo.jpg", token)).andExpect(status().isCreated()).andReturn());
    }

    private static BufferedImage fetchImage(MvcResult r) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()));
    }

    private static byte[] encode(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage picture(int w, int h, int type) {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return img;
    }

    @Test
    void anApprovedSellerUploadsAPhotoAndListsAProductWithIt() throws Exception {
        String seller = sellerToken(true);
        JsonNode up = uploadOk(encode(picture(3000, 2000, BufferedImage.TYPE_INT_RGB), "png"), seller);

        String url = up.get("url").asText();
        assertThat(url).matches("/api/images/[0-9a-f]{32}\\.jpg"); // an opaque PNG is saved as a JPEG
        assertThat(up.get("width").asInt()).isEqualTo(1600);
        assertThat(up.get("height").asInt()).isEqualTo(1067);

        // Anyone can view it, and browsers may cache it for good.
        MvcResult img = mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn();
        BufferedImage served = fetchImage(img);
        assertThat(served.getWidth()).isEqualTo(1600);
        assertThat(served.getHeight()).isEqualTo(1067);

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("name", "Photographed lamp");
        product.put("price", "12.50");
        product.put("stock", 3);
        product.put("imageUrl", url);
        MvcResult created = mvc.perform(bearer(post("/api/seller/products"), seller).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(product))).andExpect(status().isCreated()).andReturn();
        assertThat(read(created).get("imageUrl").asText()).isEqualTo(url);
    }

    @Test
    void aSmallPhotoKeepsItsSizeAndTransparencyIsKeptAsPng() throws Exception {
        String seller = sellerToken(true);
        JsonNode small = uploadOk(encode(picture(400, 300, BufferedImage.TYPE_INT_RGB), "jpg"), seller);
        assertThat(small.get("width").asInt()).isEqualTo(400);
        assertThat(small.get("height").asInt()).isEqualTo(300);

        BufferedImage clear = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB); // fully transparent
        JsonNode png = uploadOk(encode(clear, "png"), seller);
        assertThat(png.get("url").asText()).endsWith(".png");
        BufferedImage served = fetchImage(mvc.perform(get(png.get("url").asText()))
                .andExpect(header().string("Content-Type", "image/png")).andReturn());
        assertThat(served.getRGB(10, 10) >>> 24).isZero();
    }

    @Test
    void aSidewaysPhonePhotoIsTurnedTheRightWayUp() throws Exception {
        // 40×20: red on the left, blue on the right, tagged "rotate 90° clockwise to view" (EXIF orientation 6).
        byte[] jpeg = withExifOrientation(encode(picture(40, 20, BufferedImage.TYPE_INT_RGB), "jpg"), 6);
        JsonNode up = uploadOk(jpeg, sellerToken(true));
        assertThat(up.get("width").asInt()).isEqualTo(20);
        assertThat(up.get("height").asInt()).isEqualTo(40);

        BufferedImage served = fetchImage(mvc.perform(get(up.get("url").asText())).andReturn());
        Color top = new Color(served.getRGB(10, 5));
        Color bottom = new Color(served.getRGB(10, 34));
        assertThat(top.getRed()).isGreaterThan(200);
        assertThat(top.getBlue()).isLessThan(60);
        assertThat(bottom.getBlue()).isGreaterThan(200);
        assertThat(bottom.getRed()).isLessThan(60);
    }

    @Test
    void onlyApprovedSellersAndAdminsCanUpload() throws Exception {
        byte[] photo = encode(picture(50, 50, BufferedImage.TYPE_INT_RGB), "jpg");
        mvc.perform(upload(photo, "a.jpg", null)).andExpect(status().isUnauthorized());
        mvc.perform(upload(photo, "a.jpg", registerCustomer())).andExpect(status().isForbidden());
        mvc.perform(upload(photo, "a.jpg", sellerToken(false))).andExpect(status().isForbidden());
        mvc.perform(upload(photo, "a.jpg", adminToken())).andExpect(status().isCreated());
    }

    @Test
    void filesThatArentUsablePhotosAreRefused() throws Exception {
        String seller = sellerToken(true);
        // A web page named like a photo.
        mvc.perform(upload("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8), "evil.jpg", seller))
                .andExpect(status().isBadRequest());
        // Starts like a JPEG but isn't one.
        mvc.perform(upload(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5}, "broken.jpg", seller))
                .andExpect(status().isBadRequest());
        // Nothing chosen.
        mvc.perform(upload(new byte[0], "empty.jpg", seller)).andExpect(status().isBadRequest());
        mvc.perform(bearer(multipart("/api/images"), seller)).andExpect(status().isBadRequest());
        // A tiny file claiming to be 30000×30000 pixels is refused before it's decoded.
        MvcResult bomb = mvc.perform(upload(pngHeaderOnly(30_000, 30_000), "huge.png", seller))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(read(bomb).get("message").asText()).contains("too big");
    }

    @Test
    void unknownOrMalformedPhotoNamesAreNotFound() throws Exception {
        mvc.perform(get("/api/images/" + "0".repeat(32) + ".jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/api/images/application.yml")).andExpect(status().isNotFound());
        mvc.perform(get("/api/images/..%2F..%2Fpom.xml")).andExpect(status().is4xxClientError());
    }

    @Test
    void aProductImageMustBeAWebAddressOrAnUploadedPhoto() throws Exception {
        String seller = sellerToken(true);
        for (String bad : new String[]{"/api/images/../../secret.jpg", "/api/images/nothex.jpg", "javascript:alert(1)", "/etc/passwd"}) {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("name", "Lamp");
            product.put("price", "1.00");
            product.put("stock", 1);
            product.put("imageUrl", bad);
            mvc.perform(bearer(post("/api/seller/products"), seller).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(product))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void aDemoProductCanBeSavedWithItsDrawingUnchanged() throws Exception {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("name", "Demo camera");
        product.put("price", "30.00");
        product.put("stock", 2);
        product.put("imageUrl", "demo:camera:210");
        mvc.perform(bearer(post("/api/admin/products"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(product))).andExpect(status().isCreated());
    }

    /** Puts an EXIF block with the given orientation right after the JPEG's start marker. */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8); // big-endian, IFD0 right after header
        tiff.putShort((short) 1); // one entry
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0); // no next IFD
        byte[] exif = new byte[6 + tiff.capacity()];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), 0, exif, 0, 6);
        System.arraycopy(tiff.array(), 0, exif, 6, tiff.capacity());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        int len = exif.length + 2;
        out.write(0xFF);
        out.write(0xE1);
        out.write(len >> 8);
        out.write(len & 0xFF);
        out.write(exif, 0, exif.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** A PNG signature and header chunk only: enough for a reader to learn the (claimed) size. */
    private static byte[] pngHeaderOnly(int w, int h) {
        ByteBuffer ihdr = ByteBuffer.allocate(4 + 13);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(w).putInt(h)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }
}
