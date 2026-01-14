package ee.jwright.maven;

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
 * Maven build tool implementation.
 * <p>
 * Provides compilation and test execution support for Maven-based projects.
 * </p>
 *
 * @since 1.0.0
 */
@Component
@Order(100)
public class MavenBuildTool implements BuildTool {

    private static final Logger log = LoggerFactory.getLogger(MavenBuildTool.class);
    private static final int COMPILE_TIMEOUT_SECONDS = 300;
    private static final int TEST_TIMEOUT_SECONDS = 600;

    // Pattern to match Maven compilation errors: [ERROR] /path/File.java:[line,col] message
    private static final Pattern COMPILATION_ERROR_PATTERN =
            Pattern.compile("^\\[ERROR\\]\\s+(/[^:]+\\.java):\\[(\\d+),(\\d+)\\]\\s+(.+)$");

    // Pattern to match test results: Tests run: X, Failures: Y, Errors: Z
    private static final Pattern TEST_RESULT_PATTERN =
            Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+)");

    private Path currentProjectDir;

    @Override
    public String getId() {
        return "maven";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean supports(Path projectDir) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return false;
        }
        return Files.exists(projectDir.resolve("pom.xml"));
    }

    /**
     * Returns the Maven command to use for the given project directory.
     * <p>
     * Prefers the Maven wrapper (./mvnw) if it exists in the project directory,
     * otherwise falls back to the system Maven command (mvn).
     * </p>
     *
     * @param projectDir the project directory
     * @return "./mvnw" if wrapper exists, otherwise "mvn"
     */
    public String getMavenCommand(Path projectDir) {
        if (Files.exists(projectDir.resolve("mvnw"))) {
            return "./mvnw";
        }
        return "mvn";
    }

    @Override
    public CompilationResult compile(Path projectDir) {
        this.currentProjectDir = projectDir;
        String mavenCommand = getMavenCommand(projectDir);

        log.debug("Compiling project at {} using {}", projectDir, mavenCommand);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(mavenCommand, "compile", "-B")
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
     * Parses Maven compilation error output to extract structured error information.
     *
     * @param output the Maven output lines
     * @return list of parsed compilation errors
     */
    private List<CompilationError> parseCompilationErrors(List<String> output) {
        List<CompilationError> errors = new ArrayList<>();

        for (String line : output) {
            Matcher matcher = COMPILATION_ERROR_PATTERN.matcher(line);
            if (matcher.matches()) {
                Path file = Path.of(matcher.group(1));
                int lineNumber = Integer.parseInt(matcher.group(2));
                String message = matcher.group(4);

                errors.add(new CompilationError(file, lineNumber, message));
                log.debug("Parsed compilation error: {}:{} - {}", file, lineNumber, message);
            }
        }

        return errors;
    }

    @Override
    public TestResult runTests(String testClass) {
        return executeTests(testClass);
    }

    @Override
    public TestResult runSingleTest(String testClass, String testMethod) {
        return executeTests(testClass + "#" + testMethod);
    }

    /**
     * Executes tests with the given test pattern.
     *
     * @param testPattern the test pattern (class name or class#method)
     * @return the test result
     */
    private TestResult executeTests(String testPattern) {
        if (currentProjectDir == null) {
            log.error("No project directory set. Call compile() first.");
            return new TestResult(false, 0, 0, List.of());
        }

        String mavenCommand = getMavenCommand(currentProjectDir);
        log.debug("Running tests {} in {}", testPattern, currentProjectDir);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    mavenCommand, "test", "-Dtest=" + testPattern, "-B")
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

            return parseTestResults(output);

        } catch (IOException | InterruptedException e) {
            log.error("Test execution failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new TestResult(false, 0, 0, List.of());
        }
    }

    /**
     * Parses Maven test output and Surefire XML reports to extract test results.
     * <p>
     * Takes the last occurrence of test results since Maven outputs
     * per-class results followed by summary results. Also parses
     * Surefire XML reports to extract failure details.
     * </p>
     *
     * @param output the Maven output lines
     * @return the parsed test result
     */
    private TestResult parseTestResults(List<String> output) {
        int totalRun = 0;
        int failures = 0;
        int errors = 0;

        // Take only the last occurrence of test results (summary line)
        for (String line : output) {
            Matcher matcher = TEST_RESULT_PATTERN.matcher(line);
            if (matcher.find()) {
                totalRun = Integer.parseInt(matcher.group(1));
                failures = Integer.parseInt(matcher.group(2));
                errors = Integer.parseInt(matcher.group(3));
            }
        }

        int failed = failures + errors;
        int passed = totalRun - failed;
        boolean success = failed == 0 && totalRun > 0;

        // Parse Surefire XML reports for failure details
        List<TestFailure> testFailures = parseSurefireReports();

        log.debug("Test results: {} passed, {} failed (total {})", passed, failed, totalRun);
        return new TestResult(success, passed, failed, testFailures);
    }

    /**
     * Parses Surefire XML reports from target/surefire-reports/ to extract test failure details.
     *
     * @return list of test failures with details
     */
    private List<TestFailure> parseSurefireReports() {
        List<TestFailure> failures = new ArrayList<>();

        if (currentProjectDir == null) {
            return failures;
        }

        Path surefireReportsDir = currentProjectDir.resolve("target/surefire-reports");
        if (!Files.isDirectory(surefireReportsDir)) {
            log.debug("Surefire reports directory not found: {}", surefireReportsDir);
            return failures;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(surefireReportsDir, "TEST-*.xml")) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            for (Path xmlFile : stream) {
                failures.addAll(parseXmlReport(builder, xmlFile));
            }
        } catch (Exception e) {
            log.warn("Failed to parse Surefire reports", e);
        }

        return failures;
    }

    /**
     * Parses a single Surefire XML report file.
     *
     * @param builder the XML document builder
     * @param xmlFile the XML report file path
     * @return list of test failures from this report
     */
    private List<TestFailure> parseXmlReport(DocumentBuilder builder, Path xmlFile) {
        List<TestFailure> failures = new ArrayList<>();

        try {
            Document doc = builder.parse(xmlFile.toFile());
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

                    failures.add(new TestFailure(testClass, testMethod, message, stackTrace));
                    log.debug("Parsed test failure: {}#{} - {}", testClass, testMethod, message);
                }

                // Check for error element
                NodeList errorNodes = testcase.getElementsByTagName("error");
                if (errorNodes.getLength() > 0) {
                    Element error = (Element) errorNodes.item(0);
                    String message = error.getAttribute("message");
                    String stackTrace = error.getTextContent();

                    failures.add(new TestFailure(testClass, testMethod, message, stackTrace));
                    log.debug("Parsed test error: {}#{} - {}", testClass, testMethod, message);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse XML report: {}", xmlFile, e);
        }

        return failures;
    }
}
