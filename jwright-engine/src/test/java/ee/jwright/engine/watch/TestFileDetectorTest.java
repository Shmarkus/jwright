package ee.jwright.engine.watch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestFileDetector.
 */
class TestFileDetectorTest {

    @Test
    void shouldDetectJavaTestFile() {
        // Arrange
        TestFileDetector detector = new TestFileDetector();
        Path testFile = Paths.get("/project/src/test/java/com/example/MyTest.java");

        // Act
        boolean isTestFile = detector.isTestFile(testFile);

        // Assert
        assertTrue(isTestFile, "Should detect Java test file");
    }

    @Test
    void shouldNotDetectNonTestFile() {
        // Arrange
        TestFileDetector detector = new TestFileDetector();
        Path sourceFile = Paths.get("/project/src/main/java/com/example/MyClass.java");

        // Act
        boolean isTestFile = detector.isTestFile(sourceFile);

        // Assert
        assertFalse(isTestFile, "Should not detect non-test file");
    }

    @Test
    void shouldNotDetectNonJavaFile() {
        // Arrange
        TestFileDetector detector = new TestFileDetector();
        Path configFile = Paths.get("/project/src/test/resources/config.xml");

        // Act
        boolean isTestFile = detector.isTestFile(configFile);

        // Assert
        assertFalse(isTestFile, "Should not detect non-Java file");
    }

    @Test
    void shouldDetectTestFileByNamingConvention() {
        // Arrange
        TestFileDetector detector = new TestFileDetector();
        Path testFile = Paths.get("/project/tests/SomethingTest.java");

        // Act
        boolean isTestFile = detector.isTestFile(testFile);

        // Assert
        assertTrue(isTestFile, "Should detect test file by naming convention");
    }
}
