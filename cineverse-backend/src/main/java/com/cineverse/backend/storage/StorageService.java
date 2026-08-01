package com.cineverse.backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk implementation for now (LocalStorageService); swap for an S3
 * implementation later without touching any caller.
 */
public interface StorageService {

    /** @return the public URL the stored file can be fetched from */
    String store(MultipartFile file);

    /** Best-effort delete; a missing/already-gone file is not an error. */
    void delete(String url);
}
