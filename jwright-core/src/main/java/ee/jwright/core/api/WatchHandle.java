package ee.jwright.core.api;

import java.nio.file.Path;

/**
 * Handle for controlling a watch mode session.
 * <p>
 * Returned by {@link JwrightCore#watch} and allows querying status and stopping the watch.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see WatchRequest
 * @see WatchCallback
 */
public interface WatchHandle {

    /**
     * Checks if the watch is still running.
     *
     * @return true if the watch is active
     */
    boolean isRunning();

    /**
     * Stops the watch.
     * <p>
     * After calling this, {@link #isRunning()} will return false.
     * Any pending operations will be cancelled.
     * </p>
     */
    void stop();

    /**
     * Returns the directory being watched.
     *
     * @return the watched directory path
     */
    Path getWatchedDirectory();
}
