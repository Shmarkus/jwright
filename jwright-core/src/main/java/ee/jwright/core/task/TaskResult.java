package ee.jwright.core.task;

/**
 * Result of executing a single task in the pipeline.
 * <p>
 * Captures the task identifier, execution status, optional message, and number of attempts.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param taskId   the unique identifier of the task (e.g., "implement", "refactor")
 * @param status   the execution status
 * @param message  optional message providing details (null for skipped tasks)
 * @param attempts number of attempts made (0 for skipped tasks, 1+ for executed tasks)
 */
public record TaskResult(
    String taskId,
    TaskStatus status,
    String message,
    int attempts
) {}
