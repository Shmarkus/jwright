package ee.jwright.engine.watch;

import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.api.WatchHandle;
import ee.jwright.core.api.WatchRequest;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for WatchSession.
 */
class WatchSessionTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStartAndStopWatchSession() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        WatchCallback callback = mock(WatchCallback.class);

        WatchRequest request = new WatchRequest(
            tempDir,
            List.of(tempDir),
            List.of(),
            Duration.ofMillis(50),
            null
        );

        // Act
        WatchSession session = new WatchSession(request, buildTool, callback);
        WatchHandle handle = session.start();

        // Assert
        assertTrue(handle.isRunning(), "Watch session should be running");
        assertEquals(tempDir, handle.getWatchedDirectory());

        // Act - stop
        handle.stop();

        // Assert
        assertFalse(handle.isRunning(), "Watch session should be stopped");
    }

    @Test
    void shouldDetectTestFileChanges() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        List<Path> changedFiles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        WatchCallback callback = mock(WatchCallback.class);
        doAnswer(inv -> {
            changedFiles.add(inv.getArgument(0));
            latch.countDown();
            return null;
        }).when(callback).onFileChanged(any(Path.class));

        // Mock successful test result
        TestResult result = new TestResult(true, 1, 0, List.of());
        when(buildTool.runTests(anyString())).thenReturn(result);

        WatchRequest request = new WatchRequest(
            tempDir,
            List.of(tempDir),
            List.of(),
            Duration.ofMillis(50),
            null
        );

        // Act
        WatchSession session = new WatchSession(request, buildTool, callback);
        WatchHandle handle = session.start();

        // Create a test file after watch starts
        Path testFile = tempDir.resolve("MyTest.java");
        Files.writeString(testFile, """
            package com.example;

            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                void testSomething() {
                }
            }
            """);

        // Assert
        boolean detected = latch.await(2, TimeUnit.SECONDS);
        assertTrue(detected, "File change should be detected");
        assertTrue(changedFiles.stream().anyMatch(p -> p.endsWith("MyTest.java")));

        // Cleanup
        handle.stop();
    }
}
