package ee.jwright.engine.watch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DebounceHandler.
 */
class DebounceHandlerTest {

    @Test
    void shouldDebounceMultipleChangesToSameFile() throws Exception {
        // Arrange
        List<Path> processedFiles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        DebounceHandler handler = new DebounceHandler(
            Duration.ofMillis(100),
            path -> {
                processedFiles.add(path);
                latch.countDown();
            }
        );

        Path testFile = Paths.get("/test/file.txt");

        // Act - send multiple rapid changes
        handler.onFileChanged(testFile);
        handler.onFileChanged(testFile);
        handler.onFileChanged(testFile);

        // Assert - should only process once after debounce period
        boolean processed = latch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(processed, "File should be processed after debounce");
        assertEquals(1, processedFiles.size(), "Should process file only once");
        assertEquals(testFile, processedFiles.get(0));

        // Cleanup
        handler.shutdown();
    }

    @Test
    void shouldHandleMultipleDifferentFilesIndependently() throws Exception {
        // Arrange
        List<Path> processedFiles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        DebounceHandler handler = new DebounceHandler(
            Duration.ofMillis(100),
            path -> {
                synchronized (processedFiles) {
                    processedFiles.add(path);
                }
                latch.countDown();
            }
        );

        Path file1 = Paths.get("/test/file1.txt");
        Path file2 = Paths.get("/test/file2.txt");

        // Act
        handler.onFileChanged(file1);
        handler.onFileChanged(file2);

        // Assert
        boolean processed = latch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(processed, "Both files should be processed");
        assertEquals(2, processedFiles.size(), "Should process both files");
        assertTrue(processedFiles.contains(file1));
        assertTrue(processedFiles.contains(file2));

        // Cleanup
        handler.shutdown();
    }

    @Test
    void shouldResetDebounceTimerOnSubsequentChanges() throws Exception {
        // Arrange
        List<Path> processedFiles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        DebounceHandler handler = new DebounceHandler(
            Duration.ofMillis(200),
            path -> {
                processedFiles.add(path);
                latch.countDown();
            }
        );

        Path testFile = Paths.get("/test/file.txt");

        // Act - send changes at intervals less than debounce period
        handler.onFileChanged(testFile);
        Thread.sleep(50);
        handler.onFileChanged(testFile);
        Thread.sleep(50);
        handler.onFileChanged(testFile);

        // Assert - should still only process once after final debounce
        boolean processed = latch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(processed, "File should eventually be processed");
        assertEquals(1, processedFiles.size(), "Should process file only once");

        // Cleanup
        handler.shutdown();
    }
}
