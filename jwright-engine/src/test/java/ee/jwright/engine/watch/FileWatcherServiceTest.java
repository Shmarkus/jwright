package ee.jwright.engine.watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FileWatcherService.
 */
class FileWatcherServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDetectFileCreation() throws Exception {
        // Arrange
        List<Path> changedFiles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        FileWatcherService watcher = new FileWatcherService(tempDir, path -> {
            changedFiles.add(path);
            latch.countDown();
        });
        watcher.start();

        // Act
        Path newFile = tempDir.resolve("test.txt");
        Files.writeString(newFile, "content");

        // Assert
        boolean detected = latch.await(2, TimeUnit.SECONDS);
        assertTrue(detected, "File creation should be detected");
        assertTrue(changedFiles.size() >= 1, "At least one event should be detected");
        assertTrue(changedFiles.stream().anyMatch(p -> p.endsWith("test.txt")),
                   "test.txt should be in changed files");

        // Cleanup
        watcher.stop();
    }

    @Test
    void shouldDetectFileModification() throws Exception {
        // Arrange
        Path existingFile = tempDir.resolve("existing.txt");
        Files.writeString(existingFile, "initial");

        List<Path> changedFiles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        FileWatcherService watcher = new FileWatcherService(tempDir, path -> {
            changedFiles.add(path);
            latch.countDown();
        });
        watcher.start();

        // Act
        Files.writeString(existingFile, "modified");

        // Assert
        boolean detected = latch.await(2, TimeUnit.SECONDS);
        assertTrue(detected, "File modification should be detected");
        assertTrue(changedFiles.stream().anyMatch(p -> p.endsWith("existing.txt")),
                   "existing.txt should be in changed files");

        // Cleanup
        watcher.stop();
    }

    @Test
    void stopShouldStopWatching() throws Exception {
        // Arrange
        List<Path> changedFiles = new ArrayList<>();
        FileWatcherService watcher = new FileWatcherService(tempDir, changedFiles::add);
        watcher.start();

        // Act
        watcher.stop();
        Path newFile = tempDir.resolve("after-stop.txt");
        Files.writeString(newFile, "content");
        Thread.sleep(100);

        // Assert
        assertTrue(changedFiles.isEmpty() || changedFiles.stream().noneMatch(p -> p.endsWith("after-stop.txt")),
                   "No events should be detected after stop");
    }
}
