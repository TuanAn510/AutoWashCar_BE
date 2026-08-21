package com.shinecraft.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class UploadImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(UploadImageCleanupService.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "svg", "tif", "tiff", "webp");

    private final Path uploadRoot;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public UploadImageCleanupService(
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${app.upload.image-retention:PT24H}") Duration retention) {
        this(Paths.get(uploadDir), retention, Clock.systemUTC());
    }

    UploadImageCleanupService(Path uploadRoot, Duration retention, Clock clock) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Upload image retention must be positive");
        }
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.upload.cleanup-interval-ms:3600000}",
            initialDelayString = "${app.upload.cleanup-initial-delay-ms:30000}")
    public void removeExpiredImages() {
        try {
            int deletedCount = removeExpiredImages(Instant.now(clock));
            if (deletedCount > 0) {
                log.debug("Removed {} expired image(s) from upload storage", deletedCount);
            }
        } catch (IOException exception) {
            log.warn("Could not scan upload storage for expired images", exception);
        }
    }

    int removeExpiredImages(Instant now) throws IOException {
        if (!Files.isDirectory(uploadRoot, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }

        Instant expirationCutoff = now.minus(retention);
        int deletedCount = 0;

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(uploadRoot)) {
            for (Path entry : entries) {
                Path candidate = entry.toAbsolutePath().normalize();
                if (!candidate.getParent().equals(uploadRoot) || !isImageFile(candidate)) {
                    continue;
                }

                try {
                    BasicFileAttributes attributes = Files.readAttributes(
                            candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!attributes.isRegularFile()
                            || attributes.lastModifiedTime().toInstant().isAfter(expirationCutoff)) {
                        continue;
                    }
                    if (Files.deleteIfExists(candidate)) {
                        deletedCount++;
                    }
                } catch (NoSuchFileException ignored) {
                    // A concurrent request may already have removed or replaced the image.
                } catch (IOException exception) {
                    log.warn("Could not remove expired upload image {}", candidate.getFileName(), exception);
                }
            }
        }

        return deletedCount;
    }

    private boolean isImageFile(Path candidate) {
        String fileName = candidate.getFileName().toString();
        if (fileName.startsWith("image-")) {
            return true;
        }
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }
}
