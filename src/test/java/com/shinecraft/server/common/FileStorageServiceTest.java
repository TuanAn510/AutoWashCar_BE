package com.shinecraft.server.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void marksImageUploadsAndKeepsStoredNamesInsideUploadRoot() {
        FileStorageService storageService = storageService();
        MockMultipartFile image = new MockMultipartFile(
                "file", "../vehicle.PnG", "image/png", new byte[] {1, 2, 3});

        String url = storageService.store(image);
        Path storedFile = uploadRoot.resolve(url.substring("/uploads/".length()));

        assertThat(url).startsWith("/uploads/image-").endsWith(".png");
        assertThat(storedFile.normalize().getParent()).isEqualTo(uploadRoot.toAbsolutePath().normalize());
        assertThat(storedFile).exists();
    }

    @Test
    void deleteCannotEscapeTheConfiguredUploadRoot() throws IOException {
        FileStorageService storageService = storageService();
        Path outsideFile = Files.writeString(uploadRoot.getParent().resolve("outside-upload-test.txt"), "keep");

        try {
            storageService.delete("/uploads/../" + outsideFile.getFileName());
            assertThat(outsideFile).exists();
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }

    private FileStorageService storageService() {
        FileStorageService storageService = new FileStorageService();
        ReflectionTestUtils.setField(storageService, "uploadDir", uploadRoot.toString());
        storageService.init();
        return storageService;
    }
}
