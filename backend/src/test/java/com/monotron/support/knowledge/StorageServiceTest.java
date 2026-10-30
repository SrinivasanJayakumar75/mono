package com.monotron.support.knowledge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.monotron.support.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class StorageServiceTest {

    @Test
    @DisplayName("uses the configured directory when it is writable")
    void usesConfiguredDirectory(@TempDir Path tempDir) {
        AppProperties properties = new AppProperties();
        properties.getStorage().setDirectory(tempDir.toString());

        StorageService storage = new StorageService(properties);

        assertThat(storage.root()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(storage.root()).isDirectory();
    }

    @Test
    @DisplayName("falls back to a temporary directory when the configured path cannot be created")
    void fallsBackToTemporaryDirectory(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("not-a-directory"));
        AppProperties properties = new AppProperties();
        properties.getStorage().setDirectory(file.resolve("uploads").toString());

        StorageService storage = new StorageService(properties);

        assertThat(storage.root()).isDirectory();
        assertThat(storage.root().toString()).contains("monotron");
        assertThat(storage.pathFor("workspace", "document"))
                .startsWith(storage.root().toString());
    }
}
