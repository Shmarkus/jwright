package ee.jwright.engine.watch;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds failing tests in a test file.
 * <p>
 * Runs tests using the build tool and extracts the list of failing test methods.
 * </p>
 */
public class FailingTestFinder {

    private static final Logger log = LoggerFactory.getLogger(FailingTestFinder.class);

    private final BuildTool buildTool;

    /**
     * Creates a new failing test finder.
     *
     * @param buildTool the build tool to use for running tests
     */
    public FailingTestFinder(BuildTool buildTool) {
        this.buildTool = buildTool;
    }

    /**
     * Finds failing tests in the given test file.
     *
     * @param testFile  the test file path
     * @param testClass the fully qualified test class name
     * @return list of failing test targets in format "TestClass#testMethod"
     */
    public List<String> findFailingTests(Path testFile, String testClass) {
        log.debug("Finding failing tests in: {}", testClass);

        // Find project directory and compile to set up build tool
        Path projectDir = findProjectDir(testFile);
        if (projectDir == null) {
            log.error("Could not find project directory for test file: {}", testFile);
            return List.of();
        }

        log.debug("Found project directory: {}", projectDir);
        buildTool.compile(projectDir);

        TestResult result = buildTool.runTests(testClass);
        log.debug("Test result: passed={}, failed={}", result.passed(), result.failed());

        List<String> failingTests = new ArrayList<>();
        if (!result.success()) {
            for (TestFailure failure : result.failures()) {
                String target = failure.testClass() + "#" + failure.testMethod();
                failingTests.add(target);
                log.debug("Found failing test: {}", target);
            }
        }

        return failingTests;
    }

    /**
     * Finds the project directory by walking up from the test file.
     * Looks for pom.xml (Maven) or build.gradle/build.gradle.kts (Gradle).
     */
    private Path findProjectDir(Path testFile) {
        Path current = testFile.getParent();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    || Files.exists(current.resolve("build.gradle"))
                    || Files.exists(current.resolve("build.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
