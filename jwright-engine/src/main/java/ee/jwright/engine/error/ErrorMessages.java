package ee.jwright.engine.error;

/**
 * Utility class providing clear, actionable error messages.
 * <p>
 * Each method generates a user-friendly error message for a specific error scenario.
 * Messages are designed to:
 * <ul>
 *   <li>Be clear and understandable</li>
 *   <li>Include relevant context</li>
 *   <li>Suggest actionable next steps</li>
 * </ul>
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but message quality will be maintained.</p>
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // Utility class
    }

    /**
     * Message for when no build tool is found in the project.
     *
     * @param projectPath the project directory path
     * @return actionable error message
     */
    public static String noBuildToolFound(String projectPath) {
        return String.format(
            "No build tool found in: %s%n" +
            "jwright requires Maven (pom.xml) or Gradle (build.gradle).%n" +
            "Please ensure your project has a valid build configuration.",
            projectPath
        );
    }

    /**
     * Message for when a test is not found.
     *
     * @param testClass  the test class name
     * @param testMethod the test method name
     * @return actionable error message
     */
    public static String testNotFound(String testClass, String testMethod) {
        return String.format(
            "Test not found: %s#%s%n" +
            "Please verify:%n" +
            "  - The test class exists in src/test/java%n" +
            "  - The method name is correct%n" +
            "  - The method is annotated with @Test",
            testClass, testMethod
        );
    }

    /**
     * Message for when implementation file is not found.
     *
     * @param testClass the test class name
     * @param implClass the expected implementation class name
     * @return actionable error message
     */
    public static String implementationNotFound(String testClass, String implClass) {
        return String.format(
            "Implementation not found for: %s%n" +
            "Expected implementation class: %s%n" +
            "Please create %s.java with the method to implement.",
            testClass, implClass, implClass
        );
    }

    /**
     * Message for extraction failures.
     *
     * @param testTarget the test target (e.g., "CalculatorTest#testAdd")
     * @param reason     the reason for failure
     * @return actionable error message
     */
    public static String extractionFailed(String testTarget, String reason) {
        return String.format(
            "Failed to extract context from: %s%n" +
            "Reason: %s%n" +
            "Please check that the test file has valid Java syntax.",
            testTarget, reason
        );
    }

    /**
     * Message for generation failures.
     *
     * @param testTarget the test target
     * @param attempts   number of attempts made
     * @param reason     the last failure reason
     * @return actionable error message
     */
    public static String generationFailed(String testTarget, int attempts, String reason) {
        return String.format(
            "Failed to generate implementation for: %s%n" +
            "Attempts: %d%n" +
            "Last error: %s%n" +
            "Consider:%n" +
            "  - Simplifying the test%n" +
            "  - Adding @Hint annotations%n" +
            "  - Increasing max-retries in config",
            testTarget, attempts, reason
        );
    }

    /**
     * Message for compilation failures.
     *
     * @param file    the file with compilation error
     * @param line    the line number
     * @param message the compiler error message
     * @return actionable error message
     */
    public static String compilationFailed(String file, int line, String message) {
        return String.format(
            "Compilation failed in %s at line %d:%n%s",
            file, line, message
        );
    }

    /**
     * Message for test failures.
     *
     * @param testClass  the test class
     * @param testMethod the test method
     * @param message    the test failure message
     * @return actionable error message
     */
    public static String testFailed(String testClass, String testMethod, String message) {
        return String.format(
            "Test failed: %s#%s%n" +
            "Assertion: %s%n" +
            "The generated implementation does not satisfy the test.",
            testClass, testMethod, message
        );
    }

    /**
     * Message for invalid configuration.
     *
     * @param property the invalid property
     * @param reason   the reason it's invalid
     * @return actionable error message
     */
    public static String configInvalid(String property, String reason) {
        return String.format(
            "Invalid configuration: %s%n" +
            "Problem: %s%n" +
            "Please check .jwright/config.yaml",
            property, reason
        );
    }

    /**
     * Message for LLM connection failures.
     *
     * @param url the LLM server URL
     * @return actionable error message
     */
    public static String llmConnectionFailed(String url) {
        return String.format(
            "Cannot connect to LLM server: %s%n" +
            "Please ensure:%n" +
            "  - Ollama is running (ollama serve)%n" +
            "  - The URL is correct%n" +
            "  - The model is downloaded (ollama pull <model>)",
            url
        );
    }

    /**
     * Message for model not available.
     *
     * @param model the model name
     * @return actionable error message
     */
    public static String modelNotAvailable(String model) {
        return String.format(
            "Model not available: %s%n" +
            "Download it with: ollama pull %s",
            model, model
        );
    }

    /**
     * Message for timeout errors.
     *
     * @param operation  the operation that timed out
     * @param timeoutSec the timeout in seconds
     * @return actionable error message
     */
    public static String operationTimedOut(String operation, int timeoutSec) {
        return String.format(
            "Operation timed out: %s%n" +
            "Timeout: %d seconds%n" +
            "Consider increasing the timeout in config or simplifying the request.",
            operation, timeoutSec
        );
    }

    /**
     * Message for when no code writer supports the target file.
     *
     * @param file the target file
     * @return actionable error message
     */
    public static String noCodeWriterForFile(String file) {
        return String.format(
            "No code writer supports file: %s%n" +
            "jwright currently supports Java files (.java).",
            file
        );
    }
}
