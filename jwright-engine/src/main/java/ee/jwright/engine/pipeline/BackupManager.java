package ee.jwright.engine.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages file backups during pipeline execution.
 * <p>
 * Provides snapshot, revert, and commit functionality to ensure safe file modifications.
 * All file writes can be reverted if a task fails.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
public class BackupManager {

    private final Deque<Snapshot> snapshots = new ArrayDeque<>();

    /**
     * Takes a snapshot of the file content.
     * <p>
     * Stores the current content of the file in memory so it can be restored later
     * via {@link #revertLast()} or {@link #revertAll()}.
     * </p>
     *
     * @param file the file to snapshot
     * @throws UncheckedIOException if the file cannot be read
     */
    public void snapshot(Path file) {
        try {
            String content = Files.readString(file);
            snapshots.push(new Snapshot(file, content, Instant.now()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to snapshot file: " + file, e);
        }
    }

    /**
     * Reverts the most recent snapshot.
     * <p>
     * Restores the file to its state when the snapshot was taken and removes
     * the snapshot from the stack. If no snapshots exist, this is a no-op.
     * </p>
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    public void revertLast() {
        if (snapshots.isEmpty()) {
            return;
        }
        Snapshot snapshot = snapshots.pop();
        restoreSnapshot(snapshot);
    }

    /**
     * Reverts all snapshots in reverse order.
     * <p>
     * Restores all files to their state when their respective snapshots were taken.
     * Clears all snapshots from the stack. If no snapshots exist, this is a no-op.
     * </p>
     *
     * @throws UncheckedIOException if any file cannot be written
     */
    public void revertAll() {
        while (!snapshots.isEmpty()) {
            Snapshot snapshot = snapshots.pop();
            restoreSnapshot(snapshot);
        }
    }

    /**
     * Commits all changes by clearing the snapshot stack.
     * <p>
     * After commit, the file modifications become permanent and cannot be reverted.
     * Subsequent calls to {@link #revertLast()} or {@link #revertAll()} will be no-ops.
     * </p>
     */
    public void commit() {
        snapshots.clear();
    }

    /**
     * Returns the number of snapshots currently stored.
     *
     * @return the snapshot count
     */
    public int getSnapshotCount() {
        return snapshots.size();
    }

    private void restoreSnapshot(Snapshot snapshot) {
        try {
            Files.writeString(snapshot.file(), snapshot.content());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to restore file: " + snapshot.file(), e);
        }
    }

    /**
     * Internal record to hold snapshot data.
     */
    record Snapshot(Path file, String content, Instant timestamp) {}
}
