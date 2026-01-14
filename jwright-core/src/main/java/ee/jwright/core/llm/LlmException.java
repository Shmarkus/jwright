package ee.jwright.core.llm;

/**
 * Exception thrown when an LLM operation fails.
 * <p>
 * This is a checked exception to ensure calling code handles LLM errors appropriately.
 * Each error scenario has a corresponding {@link ErrorCode} for programmatic handling.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This class is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see ErrorCode
 */
public class LlmException extends Exception {

    private final ErrorCode code;

    /**
     * Error codes for categorizing LLM failures.
     */
    public enum ErrorCode {
        /**
         * The LLM request timed out.
         */
        TIMEOUT,

        /**
         * The LLM server is not available (connection refused, etc.).
         */
        UNAVAILABLE,

        /**
         * Too many requests were made (rate limiting).
         */
        RATE_LIMITED,

        /**
         * The prompt exceeds the model's context window.
         */
        CONTEXT_EXCEEDED,

        /**
         * The LLM returned an invalid or unparseable response.
         */
        INVALID_RESPONSE,

        /**
         * An unexpected error occurred.
         */
        UNKNOWN
    }

    /**
     * Creates a new LlmException with the specified error code and message.
     *
     * @param code    the error code categorizing this exception
     * @param message the detail message
     */
    public LlmException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Creates a new LlmException with the specified error code, message, and cause.
     *
     * @param code    the error code categorizing this exception
     * @param message the detail message
     * @param cause   the underlying cause of this exception
     */
    public LlmException(ErrorCode code, String message, Throwable cause) {
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
