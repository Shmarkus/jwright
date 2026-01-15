package ee.jwright.engine.watch;

import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for TestChangeHandler.
 */
class TestChangeHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDetectAndReportFailingTests() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        List<String> detectedTests = new ArrayList<>();
        WatchCallback callback = mock(WatchCallback.class);
        doAnswer(inv -> {
            detectedTests.add(inv.getArgument(0));
            return null;
        }).when(callback).onTestDetected(anyString());

        TestChangeHandler handler = new TestChangeHandler(buildTool, callback);

        // Create a test file
        Path testFile = tempDir.resolve("MyTest.java");
        Files.writeString(testFile, """
            package com.example;

            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                void testMethod1() {
                    // test
                }

                @Test
                void testMethod2() {
                    // test
                }
            }
            """);

        // Mock test result with one failure
        TestResult result = new TestResult(false, 1, 1, Arrays.asList(
            new TestFailure("com.example.MyTest", "testMethod1", "assertion failed", "...")
        ));
        when(buildTool.runTests("com.example.MyTest")).thenReturn(result);

        // Act
        handler.handleChange(testFile);

        // Assert
        assertEquals(1, detectedTests.size());
        assertEquals("com.example.MyTest#testMethod1", detectedTests.get(0));
        verify(callback).onTestDetected("com.example.MyTest#testMethod1");
    }

    @Test
    void shouldIgnoreNonTestFiles() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        WatchCallback callback = mock(WatchCallback.class);
        TestChangeHandler handler = new TestChangeHandler(buildTool, callback);

        // Create a non-test file
        Path sourceFile = tempDir.resolve("MyClass.java");
        Files.writeString(sourceFile, """
            package com.example;

            public class MyClass {
            }
            """);

        // Act
        handler.handleChange(sourceFile);

        // Assert
        verify(callback, never()).onTestDetected(anyString());
        verify(buildTool, never()).runTests(anyString());
    }

    @Test
    void shouldNotReportTestsWhenAllPass() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        WatchCallback callback = mock(WatchCallback.class);
        TestChangeHandler handler = new TestChangeHandler(buildTool, callback);

        // Create a test file
        Path testFile = tempDir.resolve("MyTest.java");
        Files.writeString(testFile, """
            package com.example;

            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                void testMethod1() {
                }
            }
            """);

        // Mock successful test result
        TestResult result = new TestResult(true, 1, 0, List.of());
        when(buildTool.runTests("com.example.MyTest")).thenReturn(result);

        // Act
        handler.handleChange(testFile);

        // Assert
        verify(callback, never()).onTestDetected(anyString());
    }
}
