package com.cineverse.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cineverse.backend.storage.exception.InvalidFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class LocalStorageServiceTest {

    private Path tempDir;
    private LocalStorageService storageService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("cineverse-storage-test");
        StorageProperties properties = new StorageProperties(
                tempDir.toString(), "/uploads", "/images/no-poster.svg", "/images/no-backdrop.svg");
        storageService = new LocalStorageService(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var files = Files.walk(tempDir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void storesValidJpegAndReturnsUrlUnderBaseUrl() {
        MultipartFile file = new MockMultipartFile("file", "poster.jpg", "image/jpeg", "fake-bytes".getBytes());

        String url = storageService.store(file);

        assertThat(url).startsWith("/uploads/").endsWith(".jpg");
    }

    @Test
    void storesValidPngAndWebp() {
        assertThat(storageService.store(new MockMultipartFile("file", "a.png", "image/png", "x".getBytes())))
                .endsWith(".png");
        assertThat(storageService.store(new MockMultipartFile("file", "a.webp", "image/webp", "x".getBytes())))
                .endsWith(".webp");
    }

    @Test
    void rejectsUnsupportedContentType() {
        MultipartFile file = new MockMultipartFile("file", "poster.gif", "image/gif", "data".getBytes());

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void rejectsFileLargerThan5Mb() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", tooBig);

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("maximum size");
    }

    @Test
    void acceptsFileExactlyAt5MbLimit() {
        byte[] exactly5mb = new byte[5 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", exactly5mb);

        assertThat(storageService.store(file)).isNotBlank();
    }

    @Test
    void rejectsEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void deleteRemovesTheStoredFileFromDisk() {
        MultipartFile file = new MockMultipartFile("file", "poster.webp", "image/webp", "data".getBytes());
        String url = storageService.store(file);
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();

        storageService.delete(url);

        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    void deleteIsANoOpForNullOrBlankUrl() {
        storageService.delete(null);
        storageService.delete("");
        // no exception — nothing to assert beyond "didn't blow up"
    }
}
