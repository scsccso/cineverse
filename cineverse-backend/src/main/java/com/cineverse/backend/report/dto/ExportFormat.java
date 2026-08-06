package com.cineverse.backend.report.dto;

import org.springframework.http.MediaType;

public enum ExportFormat {
    CSV("csv", new MediaType("text", "csv")),
    PDF("pdf", MediaType.APPLICATION_PDF);

    private final String extension;
    private final MediaType mediaType;

    ExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }
}
