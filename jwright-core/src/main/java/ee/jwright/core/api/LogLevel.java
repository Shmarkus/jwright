package ee.jwright.core.api;

/**
 * Logging verbosity level for jwright operations.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This enum is part of the stable API and will not change in backwards-incompatible ways.</p>
 */
public enum LogLevel {

    /**
     * Minimal output - only warnings and errors.
     */
    QUIET,

    /**
     * Standard progress output - what most users want.
     */
    INFO,

    /**
     * Verbose output - includes prompts, responses, and debugging details.
     */
    DEBUG,

    /**
     * Everything - for development and troubleshooting only.
     */
    TRACE
}
