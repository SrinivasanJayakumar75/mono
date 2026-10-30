package com.monotron.support.knowledge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.monotron.support.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the directory used for uploaded PDFs.
 *
 * <p>Production containers often mount a persistent volume at the configured
 * path. When that path is missing or read-only, uploads used to fail with an
 * opaque {@code /app/./data} error. This service creates the configured
 * directory up front and falls back to a writable temporary directory so the
 * upload still succeeds. The fallback is intentionally logged as a warning:
 * operators should mount a persistent writable volume for real deployments.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path root;

    public StorageService(AppProperties properties) {
        this.root = resolveRoot(properties.getStorage().getDirectory());
        log.info("Document storage directory: {}", root);
    }

    public Path root() {
        return root;
    }

    public String pathFor(String workspaceId, String documentId) {
        return root.resolve(workspaceId).resolve(documentId + ".pdf").toString();
    }

    private static Path resolveRoot(String configured) {
        Path preferred = Path.of(configured).toAbsolutePath().normalize();
        try {
            Files.createDirectories(preferred);
            if (Files.isWritable(preferred)) {
                return preferred;
            }
            log.warn("Document storage directory {} is not writable", preferred);
        } catch (IOException ex) {
            log.warn("Could not create document storage directory {}: {}", preferred, ex.getMessage());
        }

        String tmp = System.getProperty("java.io.tmpdir", "/tmp");
        Path fallback = Path.of(tmp, "monotron", "uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(fallback);
            log.warn("Using temporary document storage at {}. Set MONOTRON_STORAGE_DIR to a persistent "
                    + "writable directory in production.", fallback);
            return fallback;
        } catch (IOException ex) {
            throw new IllegalStateException("No writable document storage directory is available", ex);
        }
    }
}
