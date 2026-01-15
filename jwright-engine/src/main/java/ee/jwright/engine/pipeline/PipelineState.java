package ee.jwright.engine.pipeline;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable runtime state for pipeline execution.
 * <p>
 * Tracks the current attempt number, failed attempts, generated code,
 * and task state during pipeline execution. Also provides access to
 * request-scoped dependencies like project directory and implementation file.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
public class PipelineState {

    private int attemptNumber;
    private final int maxRetries;
    private final List<FailedAttempt> failedAttempts;
    private String generatedCode;
    private Task currentTask;
    private TaskStatus lastTaskStatus;

    // Request-scoped dependencies
    private final Path projectDir;
    private final Path implFile;
    private final TemplateEngine templateEngine;
    private final LlmClient llmClient;
    private final CodeWriter codeWriter;
    private final BuildTool buildTool;
    private final boolean dryRun;

    /**
     * Creates a new pipeline state with the specified maximum retries.
     *
     * @param maxRetries the maximum number of retry attempts (0 means no retries)
     */
    public PipelineState(int maxRetries) {
        this(maxRetries, null, null, null, null, null, null, false);
    }

    /**
     * Creates a new pipeline state with all request-scoped dependencies.
     *
     * @param maxRetries     the maximum number of retry attempts
     * @param projectDir     the project directory
     * @param implFile       the implementation file to write to
     * @param templateEngine the template engine
     * @param llmClient      the LLM client
     * @param codeWriter     the code writer
     * @param buildTool      the build tool
     */
    public PipelineState(int maxRetries, Path projectDir, Path implFile,
                         TemplateEngine templateEngine, LlmClient llmClient,
                         CodeWriter codeWriter, BuildTool buildTool) {
        this(maxRetries, projectDir, implFile, templateEngine, llmClient, codeWriter, buildTool, false);
    }

    /**
     * Creates a new pipeline state with all request-scoped dependencies including dry-run flag.
     *
     * @param maxRetries     the maximum number of retry attempts
     * @param projectDir     the project directory
     * @param implFile       the implementation file to write to
     * @param templateEngine the template engine
     * @param llmClient      the LLM client
     * @param codeWriter     the code writer
     * @param buildTool      the build tool
     * @param dryRun         if true, skip file writes and validation
     */
    public PipelineState(int maxRetries, Path projectDir, Path implFile,
                         TemplateEngine templateEngine, LlmClient llmClient,
                         CodeWriter codeWriter, BuildTool buildTool, boolean dryRun) {
        this.attemptNumber = 1;
        this.maxRetries = maxRetries;
        this.failedAttempts = new ArrayList<>();
        this.projectDir = projectDir;
        this.implFile = implFile;
        this.templateEngine = templateEngine;
        this.llmClient = llmClient;
        this.codeWriter = codeWriter;
        this.buildTool = buildTool;
        this.dryRun = dryRun;
    }

    /**
     * Increments the attempt number.
     */
    public void incrementAttempt() {
        attemptNumber++;
    }

    /**
     * Returns whether another retry attempt is allowed.
     * <p>
     * Returns true if the current attempt number is less than or equal to maxRetries.
     * For example, with maxRetries=3, attempts 1, 2, and 3 can retry, but attempt 4 cannot.
     * </p>
     *
     * @return true if retry is allowed, false otherwise
     */
    public boolean canRetry() {
        return attemptNumber <= maxRetries;
    }

    /**
     * Returns the current attempt number.
     *
     * @return the attempt number (starts at 1)
     */
    public int getAttemptNumber() {
        return attemptNumber;
    }

    /**
     * Returns the maximum number of retries configured.
     *
     * @return the max retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Records a failed attempt.
     *
     * @param failure the failed attempt to record
     */
    public void recordFailure(FailedAttempt failure) {
        failedAttempts.add(failure);
    }

    /**
     * Returns whether any failures have been recorded.
     *
     * @return true if at least one failure was recorded
     */
    public boolean hasFailures() {
        return !failedAttempts.isEmpty();
    }

    /**
     * Returns an unmodifiable view of all recorded failed attempts.
     *
     * @return the list of failed attempts
     */
    public List<FailedAttempt> getFailedAttempts() {
        return Collections.unmodifiableList(failedAttempts);
    }

    /**
     * Sets the generated code for the current attempt.
     *
     * @param generatedCode the generated code
     */
    public void setGeneratedCode(String generatedCode) {
        this.generatedCode = generatedCode;
    }

    /**
     * Returns the generated code from the current or last attempt.
     *
     * @return the generated code, or null if none generated yet
     */
    public String getGeneratedCode() {
        return generatedCode;
    }

    /**
     * Sets the currently executing task.
     *
     * @param task the current task
     */
    public void setCurrentTask(Task task) {
        this.currentTask = task;
    }

    /**
     * Returns the currently executing task.
     *
     * @return the current task, or null if no task is executing
     */
    public Task getCurrentTask() {
        return currentTask;
    }

    /**
     * Sets the status of the last executed task.
     *
     * @param status the task status
     */
    public void setLastTaskStatus(TaskStatus status) {
        this.lastTaskStatus = status;
    }

    /**
     * Returns the status of the last executed task.
     *
     * @return the last task status, or null if no task has executed
     */
    public TaskStatus getLastTaskStatus() {
        return lastTaskStatus;
    }

    /**
     * Returns the project directory.
     *
     * @return the project directory, or null if not set
     */
    public Path getProjectDir() {
        return projectDir;
    }

    /**
     * Returns the implementation file path.
     *
     * @return the implementation file, or null if not set
     */
    public Path getImplFile() {
        return implFile;
    }

    /**
     * Returns the template engine.
     *
     * @return the template engine, or null if not set
     */
    public TemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    /**
     * Returns the LLM client.
     *
     * @return the LLM client, or null if not set
     */
    public LlmClient getLlmClient() {
        return llmClient;
    }

    /**
     * Returns the code writer.
     *
     * @return the code writer, or null if not set
     */
    public CodeWriter getCodeWriter() {
        return codeWriter;
    }

    /**
     * Returns the build tool.
     *
     * @return the build tool, or null if not set
     */
    public BuildTool getBuildTool() {
        return buildTool;
    }

    /**
     * Returns whether dry-run mode is enabled.
     *
     * @return true if dry-run mode is enabled
     */
    public boolean isDryRun() {
        return dryRun;
    }
}
