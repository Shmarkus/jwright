package ee.jwright.engine.watch;

import ee.jwright.core.api.WatchHandle;

import java.nio.file.Path;

/**
 * Default implementation of WatchHandle.
 * <p>
 * Provides control over a watch session including status querying and stopping.
 * </p>
 */
public class DefaultWatchHandle implements WatchHandle {

    private final Path watchedDirectory;
    private volatile boolean running;

    /**
     * Creates a new watch handle.
     *
     * @param watchedDirectory the directory being watched
     */
    public DefaultWatchHandle(Path watchedDirectory) {
        this.watchedDirectory = watchedDirectory;
        this.running = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public Path getWatchedDirectory() {
        return watchedDirectory;
    }
}
