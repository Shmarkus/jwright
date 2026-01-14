package ee.jwright.cli;

/**
 * Exit codes for the jwright CLI.
 * <p>
 * These codes follow common Unix conventions and allow scripts
 * to programmatically determine the outcome of a jwright command.
 * </p>
 *
 * @since 1.0.0
 */
public final class ExitCode {

    private ExitCode() {
        // Utility class
    }

    /**
     * Success - the operation completed successfully.
     */
    public static final int SUCCESS = 0;

    /**
     * Implementation failed - the generated code did not pass tests.
     */
    public static final int IMPLEMENTATION_FAILED = 1;

    /**
     * Configuration error - invalid or missing configuration.
     */
    public static final int CONFIG_ERROR = 2;

    /**
     * Build tool not found - Maven/Gradle not available.
     */
    public static final int BUILD_TOOL_NOT_FOUND = 3;

    /**
     * LLM unavailable - cannot connect to LLM provider.
     */
    public static final int LLM_UNAVAILABLE = 4;

    /**
     * Invalid arguments - command line arguments are invalid.
     */
    public static final int INVALID_ARGS = 5;
}
