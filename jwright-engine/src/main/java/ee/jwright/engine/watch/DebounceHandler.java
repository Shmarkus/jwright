package ee.jwright.engine.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Handles debouncing of file change events.
 * <p>
 * Coalesces rapid file changes by waiting for a quiet period before
 * notifying the consumer. Multiple changes to the same file within
 * the debounce window will result in only one notification.
 * </p>
 */
public class DebounceHandler {

    private static final Logger log = LoggerFactory.getLogger(DebounceHandler.class);

    private final Duration debounce;
    private final Consumer<Path> onFileStabilized;
    private final ScheduledExecutorService scheduler;
    private final Map<Path, ScheduledFuture<?>> pendingTasks;

    /**
     * Creates a new debounce handler.
     *
     * @param debounce          the debounce duration
     * @param onFileStabilized  callback invoked when a file has stabilized
     */
    public DebounceHandler(Duration debounce, Consumer<Path> onFileStabilized) {
        this.debounce = debounce;
        this.onFileStabilized = onFileStabilized;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "debounce-handler");
            thread.setDaemon(true);
            return thread;
        });
        this.pendingTasks = new ConcurrentHashMap<>();
    }

    /**
     * Handles a file change event.
     * <p>
     * If this is the first change for the file, schedules a delayed notification.
     * If there's already a pending notification, cancels it and schedules a new one.
     * </p>
     *
     * @param changedFile the file that changed
     */
    public void onFileChanged(Path changedFile) {
        // Cancel any existing pending task for this file
        ScheduledFuture<?> existing = pendingTasks.get(changedFile);
        if (existing != null) {
            existing.cancel(false);
        }

        // Schedule new task
        ScheduledFuture<?> newTask = scheduler.schedule(
            () -> processFile(changedFile),
            debounce.toMillis(),
            TimeUnit.MILLISECONDS
        );

        pendingTasks.put(changedFile, newTask);
        log.trace("Debouncing change to: {}", changedFile);
    }

    private void processFile(Path file) {
        pendingTasks.remove(file);
        log.debug("File stabilized after debounce: {}", file);
        try {
            onFileStabilized.accept(file);
        } catch (Exception e) {
            log.error("Error processing file {}: {}", file, e.getMessage(), e);
        }
    }

    /**
     * Shuts down the debounce handler.
     * <p>
     * Cancels all pending tasks and shuts down the scheduler.
     * </p>
     */
    public void shutdown() {
        pendingTasks.values().forEach(task -> task.cancel(false));
        pendingTasks.clear();
        scheduler.shutdownNow();
    }
}
