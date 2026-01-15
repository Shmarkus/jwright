package ee.jwright.engine.watch;

import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.build.BuildTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles test file changes by detecting failing tests and notifying callbacks.
 * <p>
 * Orchestrates the detection flow: test file detection, test execution, and
 * failure reporting.
 * </p>
 */
public class TestChangeHandler {

    private static final Logger log = LoggerFactory.getLogger(TestChangeHandler.class);

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile("^\\s*(?:public\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);

    private final TestFileDetector testFileDetector;
    private final FailingTestFinder failingTestFinder;
    private final WatchCallback callback;

    /**
     * Creates a new test change handler.
     *
     * @param buildTool the build tool for running tests
     * @param callback  the callback to notify of detected tests
     */
    public TestChangeHandler(BuildTool buildTool, WatchCallback callback) {
        this.testFileDetector = new TestFileDetector();
        this.failingTestFinder = new FailingTestFinder(buildTool);
        this.callback = callback;
    }

    /**
     * Handles a file change event.
     * <p>
     * Checks if the file is a test file, runs tests, and reports any failures.
     * </p>
     *
     * @param changedFile the file that changed
     */
    public void handleChange(Path changedFile) {
        log.debug("Handling change for: {}", changedFile);

        // Check if it's a test file
        if (!testFileDetector.isTestFile(changedFile)) {
            log.debug("Ignoring non-test file: {}", changedFile);
            return;
        }

        log.debug("Processing test file: {}", changedFile);

        // Extract test class name
        String testClass = extractTestClassName(changedFile);
        if (testClass == null) {
            log.warn("Could not extract test class name from: {}", changedFile);
            return;
        }

        log.debug("Extracted test class: {}", testClass);

        // Find failing tests
        List<String> failingTests = failingTestFinder.findFailingTests(changedFile, testClass);
        log.debug("Found {} failing tests", failingTests.size());

        // Report each failing test
        for (String failingTest : failingTests) {
            callback.onTestDetected(failingTest);
        }
    }

    private String extractTestClassName(Path testFile) {
        try {
            String content = Files.readString(testFile);

            // Extract package
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";

            // Extract class name
            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            if (!classMatcher.find()) {
                return null;
            }
            String className = classMatcher.group(1);

            // Combine to fully qualified name
            return packageName.isEmpty() ? className : packageName + "." + className;

        } catch (IOException e) {
            log.error("Failed to read test file: {}", testFile, e);
            return null;
        }
    }
}
