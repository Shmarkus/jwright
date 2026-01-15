package ee.jwright.engine.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Service that monitors a directory for file changes using Java's WatchService.
 * <p>
 * Wraps the low-level WatchService API and provides a simpler callback-based interface.
 * </p>
 */
public class FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherService.class);

    private final Path watchedDirectory;
    private final Consumer<Path> onFileChanged;
    private final ExecutorService executor;
    private WatchService watchService;
    private volatile boolean running;

    /**
     * Creates a new file watcher service.
     *
     * @param watchedDirectory the directory to watch
     * @param onFileChanged    callback invoked when a file changes
     */
    public FileWatcherService(Path watchedDirectory, Consumer<Path> onFileChanged) {
        this.watchedDirectory = watchedDirectory;
        this.onFileChanged = onFileChanged;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "file-watcher");
            thread.setDaemon(true);
            return thread;
        });
        this.running = false;
    }

    /**
     * Starts watching the directory.
     *
     * @throws IOException if unable to start watching
     */
    public void start() throws IOException {
        if (running) {
            return;
        }

        watchService = FileSystems.getDefault().newWatchService();
        watchedDirectory.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        running = true;

        executor.submit(this::watchLoop);
        log.debug("Started watching directory: {}", watchedDirectory);
    }

    /**
     * Stops watching the directory.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("Error closing watch service", e);
        }
        executor.shutdownNow();
        log.debug("Stopped watching directory: {}", watchedDirectory);
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path filename = pathEvent.context();
                Path fullPath = watchedDirectory.resolve(filename);

                log.trace("Detected change: {} in {}", kind.name(), fullPath);
                onFileChanged.accept(fullPath);
            }

            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }
}
