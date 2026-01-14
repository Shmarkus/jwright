package ee.jwright.core.task;

import ee.jwright.core.extract.ExtractionContext;

/**
 * A pluggable task in the implementation pipeline.
 * <p>
 * Tasks are executed in order of {@link #getOrder()}. Each task can decide whether
 * to run based on the current context and pipeline state. Required tasks must succeed
 * for the pipeline to continue; optional tasks are reverted on failure.
 * </p>
 *
 * <h2>Order Convention</h2>
 * <table>
 *   <tr><th>Range</th><th>Purpose</th></tr>
 *   <tr><td>100-199</td><td>Generation (implement)</td></tr>
 *   <tr><td>200-299</td><td>Improvement (refactor)</td></tr>
 *   <tr><td>300-399</td><td>Quality (lint, checkstyle)</td></tr>
 *   <tr><td>400-499</td><td>Formatting (spotless, prettier)</td></tr>
 *   <tr><td>500+</td><td>Custom/third-party</td></tr>
 * </table>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see TaskResult
 * @see TaskStatus
 */
public interface Task {

    /**
     * Returns the unique identifier for this task.
     * <p>
     * Used for logging, configuration, and result reporting.
     * Examples: "implement", "refactor", "checkstyle"
     * </p>
     *
     * @return the task identifier
     */
    String getId();

    /**
     * Returns the execution order for this task.
     * <p>
     * Lower numbers execute first. See the order convention in the class documentation.
     * </p>
     *
     * @return the execution order (lower = earlier)
     */
    int getOrder();

    /**
     * Returns whether this task is required for pipeline success.
     * <p>
     * If a required task fails, the pipeline stops and all changes are reverted.
     * If an optional task fails, only that task's changes are reverted and the pipeline continues.
     * </p>
     *
     * @return true if the task is required, false if optional
     */
    boolean isRequired();

    /**
     * Determines whether this task should run in the current context.
     * <p>
     * Called before each execution to allow tasks to skip based on conditions.
     * For example, a refactor task might skip if the previous task failed.
     * </p>
     *
     * @param extraction the extracted context from the test
     * @param state      the current pipeline state (internal, use Object for now)
     * @return true if the task should run, false to skip
     */
    boolean shouldRun(ExtractionContext extraction, Object state);

    /**
     * Executes this task.
     * <p>
     * Called only if {@link #shouldRun} returned true. Must return a TaskResult
     * indicating success, failure, or other status.
     * </p>
     *
     * @param extraction the extracted context from the test
     * @param state      the current pipeline state (internal, use Object for now)
     * @return the result of executing this task
     */
    TaskResult execute(ExtractionContext extraction, Object state);
}
