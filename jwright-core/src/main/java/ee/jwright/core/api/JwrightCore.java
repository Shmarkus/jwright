package ee.jwright.core.api;

import ee.jwright.core.exception.JwrightException;

import java.nio.file.Path;

/**
 * Main API entry point for jwright.
 * <p>
 * Provides three core operations:
 * <ul>
 *   <li>{@link #init} - Initialize a project for jwright</li>
 *   <li>{@link #implement} - Generate implementation for a test</li>
 *   <li>{@link #watch} - Watch for test changes and auto-implement</li>
 * </ul>
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see ImplementRequest
 * @see WatchRequest
 * @see PipelineResult
 */
public interface JwrightCore {

    /**
     * Initializes a project for jwright.
     * <p>
     * Creates the {@code .jwright} directory with default configuration
     * and templates. Idempotent - safe to call multiple times.
     * </p>
     *
     * @param projectDir the project directory to initialize
     * @return the initialization result with paths to created files
     * @throws JwrightException if initialization fails
     */
    InitResult init(Path projectDir) throws JwrightException;

    /**
     * Implements code for a test.
     * <p>
     * Extracts context from the specified test, generates implementation
     * using the configured LLM, and writes the code to the implementation file.
     * </p>
     *
     * @param request the implementation request
     * @return the pipeline result
     * @throws JwrightException if implementation fails
     */
    PipelineResult implement(ImplementRequest request) throws JwrightException;

    /**
     * Starts watching for file changes.
     * <p>
     * Monitors the specified paths for changes to test files and automatically
     * triggers implementation when new tests are detected. The callback is
     * invoked for each event.
     * </p>
     *
     * @param request  the watch configuration
     * @param callback the callback for watch events
     * @return a handle to control the watch
     * @throws JwrightException if watch cannot be started
     */
    WatchHandle watch(WatchRequest request, WatchCallback callback) throws JwrightException;
}
