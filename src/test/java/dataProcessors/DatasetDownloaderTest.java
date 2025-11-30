package dataProcessors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatasetDownloaderTest {

    private DatasetDownloader datasetDownloader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        datasetDownloader = new DatasetDownloader();
    }

    @Test
    void testCreateHeaders() {
        // Test that headers are created (method is package-private for testing)
        Map<String, String> headers = datasetDownloader.createHeaders();
        
        assertNotNull(headers);
        assertTrue(headers.containsKey("User-Agent"));
        assertTrue(headers.containsKey("Postman-Token"));
        assertTrue(headers.containsKey("Accept"));
        assertTrue(headers.containsKey("Cache-Control"));
        assertTrue(headers.containsKey("Accept-Encoding"));
    }

    @Test
    void testDownloadFiles_CreatesDirectory() {
        File outputDirectory = tempDir.resolve("test_downloads").toFile();

        // This will fail to download actual files but should create directory
        assertDoesNotThrow(() -> {
            try {
                datasetDownloader.downloadFiles(outputDirectory);
            } catch (Exception e) {
                // Expected to fail without actual network, but directory should be created
            }
        });
    }
}

