package com.pacific.marketplace.media;

import com.pacific.marketplace.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Keeps photos as files in app.uploads.dir/products. */
@Component
public class FileSystemImageStore implements ImageStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemImageStore.class);

    private final Path dir;

    public FileSystemImageStore(AppProperties props) {
        this.dir = Path.of(props.uploads().dir()).toAbsolutePath().normalize().resolve("products");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't create the uploads folder " + dir + " (set UPLOADS_DIR)", e);
        }
        log.info("Product photos are stored in {}", dir);
    }

    @Override
    public void save(String name, byte[] bytes) {
        Path target = resolve(name);
        try {
            // Write to a temporary file and move it into place, so a half-written photo is never served.
            Path tmp = Files.createTempFile(dir, ".upload-", ".tmp");
            try {
                Files.write(tmp, bytes);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't save photo " + name, e);
        }
    }

    @Override
    public Optional<byte[]> load(String name) {
        try {
            return Optional.of(Files.readAllBytes(resolve(name)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read photo " + name, e);
        }
    }

    private Path resolve(String name) {
        if (!ImageService.isValidName(name)) throw new IllegalArgumentException("Bad photo name: " + name);
        return dir.resolve(name);
    }
}
