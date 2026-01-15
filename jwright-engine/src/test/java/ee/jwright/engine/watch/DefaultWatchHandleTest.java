package ee.jwright.engine.watch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultWatchHandle.
 */
class DefaultWatchHandleTest {

    @Test
    void newHandleShouldBeRunning() {
        // Arrange
        Path watchedDir = Paths.get("/test/dir");

        // Act
        DefaultWatchHandle handle = new DefaultWatchHandle(watchedDir);

        // Assert
        assertTrue(handle.isRunning(), "New handle should be running");
    }

    @Test
    void stopShouldMakeHandleNotRunning() {
        // Arrange
        Path watchedDir = Paths.get("/test/dir");
        DefaultWatchHandle handle = new DefaultWatchHandle(watchedDir);

        // Act
        handle.stop();

        // Assert
        assertFalse(handle.isRunning(), "Stopped handle should not be running");
    }

    @Test
    void getWatchedDirectoryShouldReturnProvidedPath() {
        // Arrange
        Path watchedDir = Paths.get("/test/dir");
        DefaultWatchHandle handle = new DefaultWatchHandle(watchedDir);

        // Act
        Path result = handle.getWatchedDirectory();

        // Assert
        assertEquals(watchedDir, result, "Should return the watched directory");
    }
}
