package ee.jwright.core.exception;

/**
 * Base exception for all jwright errors.
 * <p>
 * This is a checked exception to ensure calling code handles errors appropriately.
 * Each error scenario has a corresponding {@link ErrorCode} for programmatic handling.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This class is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see ErrorCode
 */
public class JwrightException extends Exception {

    private final ErrorCode code;

    /**
     * Error codes for categorizing jwright failures.
     * <p>
     * Each code represents a specific failure scenario that clients can handle programmatically.
     * </p>
     */
    public enum ErrorCode {
        /**
         * No supported build tool (Maven, Gradle) found in the project.
         */
        NO_BUILD_TOOL,

        /**
         * The specified test class or method was not found.
         */
        NO_TEST_FOUND,

        /**
         * The implementation class/file for the test was not found.
         */
        NO_IMPL_FOUND,

        /**
         * Failed to extract context from the test (parsing error, etc.).
         */
        EXTRACTION_FAILED,

        /**
         * Failed to generate implementation (LLM error, max retries exceeded, etc.).
         */
        GENERATION_FAILED,

        /**
         * Generated code failed validation (compilation error, test failure).
         */
        VALIDATION_FAILED,

        /**
         * Configuration is invalid or malformed.
         */
        CONFIG_INVALID
    }

    /**
     * Creates a new JwrightException with the specified error code and message.
     *
     * @param code    the error code categorizing this exception
     * @param message the detail message
     */
    public JwrightException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Creates a new JwrightException with the specified error code, message, and cause.
     *
     * @param code    the error code categorizing this exception
     * @param message the detail message
     * @param cause   the underlying cause of this exception
     */
    public JwrightException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Returns the error code for this exception.
     *
     * @return the error code
     */
    public ErrorCode getCode() {
        return code;
    }
}
