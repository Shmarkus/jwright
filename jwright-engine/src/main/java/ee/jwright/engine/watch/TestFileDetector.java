package ee.jwright.engine.watch;

import java.nio.file.Path;

/**
 * Detects whether a file is a test file.
 * <p>
 * Identifies test files based on path patterns and naming conventions.
 * Currently supports Java test files in standard Maven/Gradle layouts.
 * </p>
 */
public class TestFileDetector {

    private static final String TEST_PATH_MARKER = "/test/";
    private static final String JAVA_EXTENSION = ".java";
    private static final String TEST_SUFFIX = "Test.java";

    /**
     * Checks if the given path represents a test file.
     *
     * @param path the file path to check
     * @return true if the path represents a test file
     */
    public boolean isTestFile(Path path) {
        String pathString = path.toString();

        // Must be a Java file
        if (!pathString.endsWith(JAVA_EXTENSION)) {
            return false;
        }

        // Check if in test directory or follows test naming convention
        return pathString.contains(TEST_PATH_MARKER) || pathString.endsWith(TEST_SUFFIX);
    }
}
