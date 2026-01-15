package ee.jwright.engine.watch;

import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.api.WatchHandle;
import ee.jwright.core.api.WatchRequest;
import ee.jwright.core.build.BuildTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A watch session that monitors directories for test file changes.
 * <p>
 * Composes FileWatcherService, DebounceHandler, and TestChangeHandler to provide
 * a complete watch mode implementation.
 * </p>
 */
public class WatchSession {

    private static final Logger log = LoggerFactory.getLogger(WatchSession.class);

    private final WatchRequest request;
    private final BuildTool buildTool;
    private final WatchCallback callback;

    private FileWatcherService fileWatcher;
    private DebounceHandler debounceHandler;
    private TestChangeHandler testChangeHandler;
    private DefaultWatchHandle handle;

    /**
     * Creates a new watch session.
     *
     * @param request   the watch request configuration
     * @param buildTool the build tool for running tests
     * @param callback  the callback for watch events
     */
    public WatchSession(WatchRequest request, BuildTool buildTool, WatchCallback callback) {
        this.request = request;
        this.buildTool = buildTool;
        this.callback = callback;
    }

    /**
     * Starts the watch session.
     *
     * @return the watch handle for controlling the session
     * @throws IOException if unable to start watching
     */
    public WatchHandle start() throws IOException {
        log.info("Starting watch session for: {}", request.projectDir());

        // Initialize components
        testChangeHandler = new TestChangeHandler(buildTool, callback);

        debounceHandler = new DebounceHandler(
            request.debounce(),
            this::handleStabilizedFile
        );

        // Determine watch directory
        Path watchDir = determineWatchDirectory();
        handle = new DefaultWatchHandle(watchDir);

        fileWatcher = new FileWatcherService(watchDir, path -> {
            callback.onFileChanged(path);
            debounceHandler.onFileChanged(path);
        });

        fileWatcher.start();

        log.info("Watch session started, monitoring: {}", watchDir);
        return handle;
    }

    private Path determineWatchDirectory() {
        // If watch paths specified, use the first one
        if (request.watchPaths() != null && !request.watchPaths().isEmpty()) {
            return request.watchPaths().get(0);
        }
        // Otherwise watch the project directory
        return request.projectDir();
    }

    private void handleStabilizedFile(Path file) {
        log.debug("File stabilized: {}", file);
        testChangeHandler.handleChange(file);
    }

    /**
     * Stops the watch session.
     */
    public void stop() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        if (debounceHandler != null) {
            debounceHandler.shutdown();
        }
        if (handle != null) {
            handle.stop();
        }
        log.info("Watch session stopped");
    }
}
