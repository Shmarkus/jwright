package ee.jwright.engine.pipeline;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.exception.JwrightException;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.engine.context.ContextBuilder;
import ee.jwright.engine.resolve.TestTargetResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Executes tasks in the implementation pipeline.
 * <p>
 * Tasks are executed in order of {@link Task#getOrder()}. Required tasks must succeed
 * for the pipeline to complete; optional tasks are reverted on failure and the pipeline continues.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
public class TaskPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);

    private final List<Task> tasks;
    private final ContextBuilder contextBuilder;
    private final BackupManager backupManager;
    private final int maxRetries;
    private final Path implFile;
    private final Path projectDir;
    private final TemplateEngine templateEngine;
    private final LlmClient llmClient;
    private final CodeWriter codeWriter;
    private final BuildTool buildTool;
    private final TestTargetResolver targetResolver;

    /**
     * Creates a new TaskPipeline with the given components.
     *
     * @param tasks          the list of tasks to execute (will be sorted by order)
     * @param contextBuilder the context builder for extraction
     * @param backupManager  the backup manager for file snapshots
     * @param maxRetries     maximum number of retry attempts for required tasks
     * @param implFile       the implementation file to modify
     */
    public TaskPipeline(List<Task> tasks, ContextBuilder contextBuilder,
                        BackupManager backupManager, int maxRetries, Path implFile) {
        this(tasks, contextBuilder, backupManager, maxRetries, implFile, null, null, null, null, null, new TestTargetResolver());
    }

    /**
     * Creates a new TaskPipeline with all dependencies.
     *
     * @param tasks          the list of tasks to execute (will be sorted by order)
     * @param contextBuilder the context builder for extraction
     * @param backupManager  the backup manager for file snapshots
     * @param maxRetries     maximum number of retry attempts for required tasks
     * @param implFile       the implementation file to modify
     * @param projectDir     the project directory
     * @param templateEngine the template engine
     * @param llmClient      the LLM client
     * @param codeWriter     the code writer
     * @param buildTool      the build tool
     * @param targetResolver the test target resolver
     */
    public TaskPipeline(List<Task> tasks, ContextBuilder contextBuilder,
                        BackupManager backupManager, int maxRetries, Path implFile,
                        Path projectDir, TemplateEngine templateEngine,
                        LlmClient llmClient, CodeWriter codeWriter, BuildTool buildTool,
                        TestTargetResolver targetResolver) {
        this.tasks = tasks.stream()
            .sorted(Comparator.comparingInt(Task::getOrder))
            .toList();
        this.contextBuilder = contextBuilder;
        this.backupManager = backupManager;
        this.maxRetries = maxRetries;
        this.implFile = implFile;
        this.projectDir = projectDir;
        this.templateEngine = templateEngine;
        this.llmClient = llmClient;
        this.codeWriter = codeWriter;
        this.buildTool = buildTool;
        this.targetResolver = targetResolver;
    }

    /**
     * Executes the pipeline for the given request.
     *
     * @param request the implementation request
     * @return the pipeline result
     */
    public PipelineResult execute(ImplementRequest request) {
        // Build extraction context (simplified - in real impl would parse target)
        ExtractionContext context = contextBuilder.build(toExtractionRequest(request));

        // Create state with all dependencies for tasks
        PipelineState state = new PipelineState(
            maxRetries,
            projectDir != null ? projectDir : request.projectDir(),
            implFile,
            templateEngine,
            llmClient,
            codeWriter,
            buildTool,
            request.dryRun()
        );

        List<TaskResult> results = new ArrayList<>();

        for (Task task : tasks) {
            // Check if task should run
            if (!task.shouldRun(context, state)) {
                log.debug("Skipping task: {}", task.getId());
                results.add(new TaskResult(task.getId(), TaskStatus.SKIPPED, null, 0));
                continue;
            }

            state.setCurrentTask(task);

            // Take snapshot before task execution
            if (implFile != null && implFile.toFile().exists()) {
                backupManager.snapshot(implFile);
            }

            // Execute with retry for required tasks
            TaskResult result = executeWithRetry(task, context, state);
            results.add(result);
            state.setLastTaskStatus(result.status());

            // Handle failure
            if (result.status() == TaskStatus.FAILED) {
                if (task.isRequired()) {
                    log.error("Required task failed: {}", task.getId());
                    backupManager.revertAll();
                    return new PipelineResult(false, results, null, null);
                } else {
                    log.warn("Optional task failed, reverting: {}", task.getId());
                    backupManager.revertLast();
                    // Update the result to REVERTED
                    results.set(results.size() - 1,
                        new TaskResult(task.getId(), TaskStatus.REVERTED, result.message(), result.attempts()));
                }
            }
        }

        backupManager.commit();
        return new PipelineResult(true, results, implFile, state.getGeneratedCode());
    }

    private TaskResult executeWithRetry(Task task, ExtractionContext context, PipelineState state) {
        TaskResult result = null;
        int attempts = 0;
        int maxAttempts = task.isRequired() ? maxRetries + 1 : 1; // 1 initial + maxRetries

        while (attempts < maxAttempts) {
            attempts++;
            state.incrementAttempt(); // Track in state (but we use local counter for loop)

            log.debug("Executing task: {} (attempt {})", task.getId(), attempts);
            result = task.execute(context, state);

            if (result.status() == TaskStatus.SUCCESS) {
                return result;
            }

            // Record failure for required tasks
            if (task.isRequired()) {
                state.recordFailure(new FailedAttempt(
                    attempts,
                    state.getGeneratedCode(),
                    result.message(),
                    null,
                    null
                ));
            }

            // If not required, no retry
            if (!task.isRequired()) {
                break;
            }
        }

        return result;
    }

    private ExtractionRequest toExtractionRequest(ImplementRequest request) {
        try {
            TestTargetResolver.ResolvedTarget resolved = targetResolver.resolve(
                request.projectDir(),
                request.target()
            );

            return new ExtractionRequest(
                resolved.testFile(),
                resolved.testClassName(),
                resolved.testMethodName(),
                resolved.implFile(),
                resolved.implMethodName(),
                resolved.sourceRoot()
            );
        } catch (JwrightException e) {
            // If resolution fails, log and use fallback (this shouldn't happen in production)
            log.error("Failed to resolve target: {}", request.target(), e);
            throw new RuntimeException("Target resolution failed: " + e.getMessage(), e);
        }
    }
}
