package com.cineverse.backend.storage;

import com.cineverse.backend.storage.exception.InvalidFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalStorageService implements StorageService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path uploadDir;
    private final String baseUrl;

    public LocalStorageService(StorageProperties properties) {
        this.uploadDir = Path.of(properties.uploadDir());
        this.baseUrl = properties.baseUrl();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize upload directory: " + uploadDir, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        validate(file);
        // Server-generated filename, never the client-supplied one — avoids
        // path traversal / collisions from untrusted input.
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        String filename = UUID.randomUUID() + extension;
        try {
            file.transferTo(uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
        return baseUrl + "/" + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank() || !url.startsWith(baseUrl + "/")) {
            return;
        }
        String filename = url.substring(url.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(uploadDir.resolve(filename));
        } catch (IOException e) {
            // Best-effort cleanup — an orphaned file on disk isn't worth failing the request over.
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File must not be empty");
        }
        if (!ALLOWED_CONTENT_TYPES.containsKey(file.getContentType())) {
            throw new InvalidFileException("Unsupported file type: only JPG, PNG, and WEBP are allowed");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("File exceeds the maximum size of 5MB");
        }
    }
}
