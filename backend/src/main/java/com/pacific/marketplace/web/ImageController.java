package com.pacific.marketplace.web;

import com.pacific.marketplace.media.ImageService;
import com.pacific.marketplace.media.ImageService.Uploaded;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Product photos: approved sellers and admins upload them; anyone can view them. */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService images;

    public ImageController(ImageService images) {
        this.images = images;
    }

    /** Returns the photo's address, to be saved as a product's imageUrl. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Uploaded upload(@RequestParam(value = "file", required = false) MultipartFile file,
                           @AuthenticationPrincipal Jwt jwt) throws IOException {
        return images.upload(CurrentUser.id(jwt), CurrentUser.role(jwt), file == null ? null : file.getBytes());
    }

    /** A photo's name is random and its content never changes, so browsers may keep it for good. */
    @GetMapping("/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) {
        return images.load(name)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'")
                        .body(bytes))
                .orElseThrow(() -> ApiException.notFound("Photo not found."));
    }
}
