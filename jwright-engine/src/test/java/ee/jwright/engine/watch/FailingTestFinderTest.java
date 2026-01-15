package ee.jwright.engine.watch;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for FailingTestFinder.
 */
class FailingTestFinderTest {

    @Test
    void shouldFindFailingTestsInFile() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        FailingTestFinder finder = new FailingTestFinder(buildTool);

        Path testFile = Paths.get("/project/src/test/java/com/example/MyTest.java");
        String testClass = "com.example.MyTest";

        // Mock test result with failures
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
        verify(buildTool).runTests(testClass);
    }

    @Test
    void shouldReturnEmptyListWhenAllTestsPass() throws Exception {
        // Arrange
        BuildTool buildTool = mock(BuildTool.class);
        FailingTestFinder finder = new FailingTestFinder(buildTool);

        Path testFile = Paths.get("/project/src/test/java/com/example/MyTest.java");
        String testClass = "com.example.MyTest";

        // Mock successful test result
        TestResult result = new TestResult(true, 3, 0, List.of());
        when(buildTool.runTests(testClass)).thenReturn(result);

        // Act
        List<String> failingTests = finder.findFailingTests(testFile, testClass);

        // Assert
        assertTrue(failingTests.isEmpty(), "Should return empty list when all tests pass");
        verify(buildTool).runTests(testClass);
    }
}
