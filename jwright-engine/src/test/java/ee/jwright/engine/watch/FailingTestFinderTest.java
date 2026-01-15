package ee.jwright.engine.watch;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for FailingTestFinder.
 */
class FailingTestFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFindFailingTestsInFile() throws Exception {
        // Arrange - create project structure
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path testDir = Files.createDirectories(tempDir.resolve("src/test/java/com/example"));
        Path testFile = Files.writeString(testDir.resolve("MyTest.java"), "class MyTest {}");

        BuildTool buildTool = mock(BuildTool.class);
        FailingTestFinder finder = new FailingTestFinder(buildTool);

        String testClass = "com.example.MyTest";

        // Mock compile and test results
        when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
        TestResult result = new TestResult(false, 1, 2, Arrays.asList(
            new TestFailure("com.example.MyTest", "testMethod1", "expected: <5> but was: <3>", "..."),
            new TestFailure("com.example.MyTest", "testMethod2", "NullPointerException", "...")
        ));
        when(buildTool.runTests(testClass)).thenReturn(result);

        // Act
        List<String> failingTests = finder.findFailingTests(testFile, testClass);

        // Assert
        assertEquals(2, failingTests.size());
        assertTrue(failingTests.contains("com.example.MyTest#testMethod1"));
        assertTrue(failingTests.contains("com.example.MyTest#testMethod2"));
        verify(buildTool).compile(tempDir);
        verify(buildTool).runTests(testClass);
    }

    @Test
    void shouldReturnEmptyListWhenAllTestsPass() throws Exception {
        // Arrange - create project structure
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path testDir = Files.createDirectories(tempDir.resolve("src/test/java/com/example"));
        Path testFile = Files.writeString(testDir.resolve("MyTest.java"), "class MyTest {}");

        BuildTool buildTool = mock(BuildTool.class);
        FailingTestFinder finder = new FailingTestFinder(buildTool);

        String testClass = "com.example.MyTest";

        // Mock compile and test results
        when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
        TestResult result = new TestResult(true, 3, 0, List.of());
        when(buildTool.runTests(testClass)).thenReturn(result);

        // Act
        List<String> failingTests = finder.findFailingTests(testFile, testClass);

        // Assert
        assertTrue(failingTests.isEmpty(), "Should return empty list when all tests pass");
        verify(buildTool).compile(tempDir);
        verify(buildTool).runTests(testClass);
    }
}
