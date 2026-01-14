package ee.jwright.core.task;

/**
 * Status of a task execution in the pipeline.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This enum is part of the stable API and will not change in backwards-incompatible ways.</p>
 */
public enum TaskStatus {

    /**
     * Task completed successfully.
     */
    SUCCESS,

    /**
     * Task failed and could not be recovered (for required tasks, pipeline stops).
     */
    FAILED,

    /**
     * Task was skipped based on {@code shouldRun()} returning false.
     */
    SKIPPED,

    /**
     * Optional task failed and its changes were reverted.
     * <p>
     * This status indicates the task attempted to make changes but failed,
     * and the backup manager restored the original state. The pipeline continues.
     * </p>
     */
    REVERTED
}
