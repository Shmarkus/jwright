package ee.jwright.core.api;

import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of executing the implementation pipeline.
 * <p>
 * Contains the overall success status, individual task results, the modified file path,
 * and the final generated code.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param success     true if the pipeline completed successfully (all required tasks passed)
 * @param taskResults list of individual task results in execution order
 * @param modifiedFile path to the modified implementation file (null if failed)
 * @param finalCode   the final generated code (null if failed)
 */
public record PipelineResult(
    boolean success,
    List<TaskResult> taskResults,
    Path modifiedFile,
    String finalCode
) {

    /**
     * Checks if any optional tasks were reverted during execution.
     * <p>
     * A reverted task indicates that an optional step (like refactoring) was attempted
     * but failed, and the changes were rolled back. This is considered a warning because
     * the pipeline still succeeded but some improvements were not applied.
     * </p>
     *
     * @return true if any task has status {@link TaskStatus#REVERTED}
     */
    public boolean hasWarnings() {
        return taskResults.stream()
            .anyMatch(r -> r.status() == TaskStatus.REVERTED);
    }
}
