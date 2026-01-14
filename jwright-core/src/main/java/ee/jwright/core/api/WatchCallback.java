package ee.jwright.core.api;

import ee.jwright.core.exception.JwrightException;

import java.nio.file.Path;

/**
 * Callback interface for watch mode events.
 * <p>
 * Implementations receive notifications about file changes, test detection,
 * generation progress, and errors during watch mode.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see WatchRequest
 * @see WatchHandle
 */
public interface WatchCallback {

    /**
     * Called when a file changes in the watched directories.
     *
     * @param file the path to the changed file
     */
    void onFileChanged(Path file);

    /**
     * Called when a test is detected that needs implementation.
     *
     * @param testTarget the test target in format "TestClass#testMethod"
     */
    void onTestDetected(String testTarget);

    /**
     * Called when code generation starts for a test.
     *
     * @param testTarget the test target being implemented
     */
    void onGenerationStarted(String testTarget);

    /**
     * Called when code generation completes (success or failure).
     *
     * @param result the pipeline result
     */
    void onGenerationComplete(PipelineResult result);

    /**
     * Called when an error occurs during watch mode.
     *
     * @param error the exception that occurred
     */
    void onError(JwrightException error);
}
