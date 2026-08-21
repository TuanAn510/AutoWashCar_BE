package com.shinecraft.server.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UploadImageCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @TempDir
    Path uploadRoot;

    @Test
    void removesOnlyRootImagesThatHaveReachedTwentyFourHours() throws IOException {
        Path expiredImage = file("expired.jpg", NOW.minus(Duration.ofHours(25)));
        Path boundaryImage = file("boundary.PNG", NOW.minus(Duration.ofHours(24)));
        Path markedImageWithoutExtension = file("image-generated", NOW.minus(Duration.ofDays(2)));
        Path freshImage = file("fresh.webp", NOW.minus(Duration.ofHours(23)));
        Path expiredDocument = file("evidence.pdf", NOW.minus(Duration.ofDays(7)));
        Path nestedDirectory = Files.createDirectory(uploadRoot.resolve("archive.jpg"));
        Path nestedImage = Files.writeString(nestedDirectory.resolve("nested.jpg"), "nested");
        Files.setLastModifiedTime(nestedImage, FileTime.from(NOW.minus(Duration.ofDays(7))));

        UploadImageCleanupService cleanupService = cleanupService(uploadRoot);

        assertThat(cleanupService.removeExpiredImages(NOW)).isEqualTo(3);
        assertThat(expiredImage).doesNotExist();
        assertThat(boundaryImage).doesNotExist();
        assertThat(markedImageWithoutExtension).doesNotExist();
        assertThat(freshImage).exists();
        assertThat(expiredDocument).exists();
        assertThat(nestedDirectory).isDirectory();
        assertThat(nestedImage).exists();
    }

    @Test
    void missingUploadDirectoryIsAValidNoOp() throws IOException {
        Path missingRoot = uploadRoot.resolve("missing");

        assertThat(cleanupService(missingRoot).removeExpiredImages(NOW)).isZero();
        assertThat(missingRoot).doesNotExist();
    }

    @Test
    void rejectsUnsafeNonPositiveRetention() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UploadImageCleanupService(uploadRoot, Duration.ZERO, clock));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UploadImageCleanupService(uploadRoot, Duration.ofHours(-1), clock));
    }

    private UploadImageCleanupService cleanupService(Path root) {
        return new UploadImageCleanupService(
                root, Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Path file(String name, Instant modifiedAt) throws IOException {
        Path path = Files.writeString(uploadRoot.resolve(name), "content");
        Files.setLastModifiedTime(path, FileTime.from(modifiedAt));
        return path;
    }
}
