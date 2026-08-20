package com.shinecraft.server.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadPath, e);
        }
    }

    public String store(MultipartFile file) {
        String extension = safeExtension(file.getOriginalFilename());
        String imagePrefix = isImage(file) ? "image-" : "";
        String storedName = imagePrefix + UUID.randomUUID() + extension;

        try {
            Path targetPath = uploadPath.resolve(storedName).normalize();
            if (!uploadPath.equals(targetPath.getParent())) {
                throw new IOException("Resolved upload path is outside configured storage");
            }
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + storedName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + storedName, e);
        }
    }

    public void delete(String url) {
        if (url == null || !url.startsWith("/uploads/")) return;
        String fileName = url.substring("/uploads/".length());
        Path targetPath = uploadPath.resolve(fileName).normalize();
        if (!uploadPath.equals(targetPath.getParent())) return;
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
        }
    }

    private boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private String safeExtension(String originalName) {
        if (originalName == null || originalName.isBlank()) return "";
        int lastSeparator = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String fileName = originalName.substring(lastSeparator + 1);
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) return "";
        String extension = fileName.substring(lastDot + 1);
        return extension.matches("[A-Za-z0-9]{1,10}") ? "." + extension.toLowerCase(Locale.ROOT) : "";
    }
}
