package ee.jwright.gradle;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gradle build tool implementation.
 * <p>
 * Provides compilation and test execution support for Gradle-based projects.
 * Supports both Groovy DSL (build.gradle) and Kotlin DSL (build.gradle.kts).
 * </p>
 *
 * @since 1.0.0
 */
@Component
@Order(200)
public class GradleBuildTool implements BuildTool {

    private static final Logger log = LoggerFactory.getLogger(GradleBuildTool.class);
    private static final int COMPILE_TIMEOUT_SECONDS = 300;
    private static final int TEST_TIMEOUT_SECONDS = 600;

    // Pattern to match Gradle compilation errors: /path/File.java:line: error: message
    private static final Pattern COMPILATION_ERROR_PATTERN =
            Pattern.compile("^(/[^:]+\\.java):(\\d+):\\s*error:\\s*(.+)$");

    // Alternative pattern for Gradle errors: e: file:///path/File.kt: (line, col): message
    private static final Pattern KOTLIN_ERROR_PATTERN =
            Pattern.compile("^e:\\s*file://(/[^:]+):\\s*\\((\\d+),\\s*\\d+\\):\\s*(.+)$");

    private Path currentProjectDir;

    @Override
    public String getId() {
        return "gradle";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public boolean supports(Path projectDir) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return false;
        }
        return Files.exists(projectDir.resolve("build.gradle"))
            || Files.exists(projectDir.resolve("build.gradle.kts"));
    }

    /**
     * Returns the Gradle command to use for the given project directory.
     * <p>
     * Prefers the Gradle wrapper (./gradlew) if it exists in the project directory,
     * otherwise falls back to the system Gradle command (gradle).
     * </p>
     *
     * @param projectDir the project directory
     * @return "./gradlew" if wrapper exists, otherwise "gradle"
     */
    public String getGradleCommand(Path projectDir) {
        if (Files.exists(projectDir.resolve("gradlew"))) {
            return "./gradlew";
        }
        return "gradle";
    }

    @Override
    public CompilationResult compile(Path projectDir) {
        this.currentProjectDir = projectDir;
        String gradleCommand = getGradleCommand(projectDir);

        log.debug("Compiling project at {} using {}", projectDir, gradleCommand);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    gradleCommand, "compileJava", "compileTestJava", "--no-daemon", "-q")
                    .directory(projectDir.toFile())
                    .redirectErrorStream(true);

            Process process = processBuilder.start();
            List<String> output = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            boolean completed = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.error("Compilation timed out after {} seconds", COMPILE_TIMEOUT_SECONDS);
                return new CompilationResult(false, List.of());
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.debug("Compilation successful");
                return new CompilationResult(true, List.of());
            } else {
                log.debug("Compilation failed with exit code {}", exitCode);
                List<CompilationError> errors = parseCompilationErrors(output);
                return new CompilationResult(false, errors);
            }

        } catch (IOException | InterruptedException e) {
            log.error("Compilation failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new CompilationResult(false, List.of());
        }
    }

    /**
     * Parses Gradle compilation error output to extract structured error information.
     *
     * @param output the Gradle output lines
     * @return list of parsed compilation errors
     */
    private List<CompilationError> parseCompilationErrors(List<String> output) {
        List<CompilationError> errors = new ArrayList<>();

        for (String line : output) {
            // Try Java error pattern
            Matcher matcher = COMPILATION_ERROR_PATTERN.matcher(line);
            if (matcher.matches()) {
                Path file = Path.of(matcher.group(1));
                int lineNumber = Integer.parseInt(matcher.group(2));
                String message = matcher.group(3);

                errors.add(new CompilationError(file, lineNumber, message));
                log.debug("Parsed compilation error: {}:{} - {}", file, lineNumber, message);
                continue;
            }

            // Try Kotlin error pattern
            Matcher kotlinMatcher = KOTLIN_ERROR_PATTERN.matcher(line);
            if (kotlinMatcher.matches()) {
                Path file = Path.of(kotlinMatcher.group(1));
                int lineNumber = Integer.parseInt(kotlinMatcher.group(2));
                String message = kotlinMatcher.group(3);

                errors.add(new CompilationError(file, lineNumber, message));
                log.debug("Parsed Kotlin compilation error: {}:{} - {}", file, lineNumber, message);
            }
        }

        return errors;
    }

    @Override
    public TestResult runTests(String testClass) {
        return executeTests(testClass, null);
    }

    @Override
    public TestResult runSingleTest(String testClass, String testMethod) {
        return executeTests(testClass, testMethod);
    }

    /**
     * Executes tests with the given test class and optional method.
     *
     * @param testClass  the test class name
     * @param testMethod the test method name (optional)
     * @return the test result
     */
    private TestResult executeTests(String testClass, String testMethod) {
        if (currentProjectDir == null) {
            log.error("No project directory set. Call compile() first.");
            return new TestResult(false, 0, 0, List.of());
        }

        String gradleCommand = getGradleCommand(currentProjectDir);
        String testFilter = testMethod != null
                ? testClass + "." + testMethod
                : testClass;

        log.debug("Running tests {} in {}", testFilter, currentProjectDir);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    gradleCommand, "test", "--tests", testFilter, "--no-daemon")
                    .directory(currentProjectDir.toFile())
                    .redirectErrorStream(true);

            Process process = processBuilder.start();
            List<String> output = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            boolean completed = process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.error("Test execution timed out after {} seconds", TEST_TIMEOUT_SECONDS);
                return new TestResult(false, 0, 0, List.of());
            }

            return parseTestResults(process.exitValue());

        } catch (IOException | InterruptedException e) {
            log.error("Test execution failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new TestResult(false, 0, 0, List.of());
        }
    }

    /**
     * Parses Gradle test results from XML reports.
     * <p>
     * Gradle stores test results in build/test-results/test/ as XML files.
     * </p>
     *
     * @param exitCode the Gradle process exit code
     * @return the parsed test result
     */
    private TestResult parseTestResults(int exitCode) {
        List<TestFailure> failures = new ArrayList<>();
        int totalTests = 0;
        int failedTests = 0;

        if (currentProjectDir == null) {
            return new TestResult(exitCode == 0, 0, 0, failures);
        }

        Path testResultsDir = currentProjectDir.resolve("build/test-results/test");
        if (!Files.isDirectory(testResultsDir)) {
            log.debug("Test results directory not found: {}", testResultsDir);
            // If no test results but exit code is 0, assume success
            return new TestResult(exitCode == 0, exitCode == 0 ? 1 : 0, exitCode == 0 ? 0 : 1, failures);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testResultsDir, "TEST-*.xml")) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            for (Path xmlFile : stream) {
                TestReportSummary summary = parseXmlReport(builder, xmlFile);
                totalTests += summary.tests;
                failedTests += summary.failures + summary.errors;
                failures.addAll(summary.testFailures);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gradle test reports", e);
        }

        int passedTests = totalTests - failedTests;
        boolean success = failedTests == 0 && totalTests > 0;

        log.debug("Test results: {} passed, {} failed (total {})", passedTests, failedTests, totalTests);
        return new TestResult(success, passedTests, failedTests, failures);
    }

    /**
     * Parses a single Gradle test XML report file.
     *
     * @param builder the XML document builder
     * @param xmlFile the XML report file path
     * @return summary of tests from this report
     */
    private TestReportSummary parseXmlReport(DocumentBuilder builder, Path xmlFile) {
        TestReportSummary summary = new TestReportSummary();

        try {
            Document doc = builder.parse(xmlFile.toFile());
            Element testsuite = doc.getDocumentElement();

            // Get summary from testsuite attributes
            summary.tests = Integer.parseInt(testsuite.getAttribute("tests"));
            summary.failures = Integer.parseInt(testsuite.getAttribute("failures"));
            summary.errors = Integer.parseInt(testsuite.getAttribute("errors"));

            // Parse individual test cases for failure details
            NodeList testcases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < testcases.getLength(); i++) {
                Element testcase = (Element) testcases.item(i);
                String testMethod = testcase.getAttribute("name");
                String testClass = testcase.getAttribute("classname");

                // Check for failure element
                NodeList failureNodes = testcase.getElementsByTagName("failure");
                if (failureNodes.getLength() > 0) {
                    Element failure = (Element) failureNodes.item(0);
                    String message = failure.getAttribute("message");
                    String stackTrace = failure.getTextContent();

                    summary.testFailures.add(new TestFailure(testClass, testMethod, message, stackTrace));
                    log.debug("Parsed test failure: {}#{} - {}", testClass, testMethod, message);
                }

                // Check for error element
                NodeList errorNodes = testcase.getElementsByTagName("error");
                if (errorNodes.getLength() > 0) {
                    Element error = (Element) errorNodes.item(0);
                    String message = error.getAttribute("message");
                    String stackTrace = error.getTextContent();

                    summary.testFailures.add(new TestFailure(testClass, testMethod, message, stackTrace));
                    log.debug("Parsed test error: {}#{} - {}", testClass, testMethod, message);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse XML report: {}", xmlFile, e);
        }

        return summary;
    }

    /**
     * Internal class to hold parsed test report summary.
     */
    private static class TestReportSummary {
        int tests;
        int failures;
        int errors;
        List<TestFailure> testFailures = new ArrayList<>();
    }
}
