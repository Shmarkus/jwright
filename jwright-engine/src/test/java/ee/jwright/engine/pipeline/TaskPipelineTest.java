package ee.jwright.engine.pipeline;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.LogLevel;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.engine.context.ContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TaskPipeline}.
 */
class TaskPipelineTest {

    private ContextBuilder contextBuilder;
    private BackupManager backupManager;
    private ImplementRequest sampleRequest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        contextBuilder = new ContextBuilder(Collections.emptyList());
        backupManager = new BackupManager();

        // Create a sample implementation file
        Path implFile = tempDir.resolve("src/main/java/Impl.java");
        Files.createDirectories(implFile.getParent());
        Files.writeString(implFile, "public class Impl {}");

        sampleRequest = new ImplementRequest(tempDir, "Test#testMethod", false, LogLevel.INFO);
    }

    @Nested
    @DisplayName("3.12 single task execution")
    class SingleTaskExecutionTests {

        @Test
        @DisplayName("execute() runs single task and returns result")
        void executeRunsSingleTaskAndReturnsResult() throws IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Task task = createSuccessTask("implement", 100, true);
            TaskPipeline pipeline = new TaskPipeline(
                List.of(task), contextBuilder, backupManager, 3, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.taskResults()).hasSize(1);
            assertThat(result.taskResults().get(0).taskId()).isEqualTo("implement");
            assertThat(result.taskResults().get(0).status()).isEqualTo(TaskStatus.SUCCESS);
        }

        @Test
        @DisplayName("execute() returns task result details")
        void executeReturnsTaskResultDetails() throws IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Task task = createSuccessTask("implement", 100, true);
            TaskPipeline pipeline = new TaskPipeline(
                List.of(task), contextBuilder, backupManager, 3, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            TaskResult taskResult = result.taskResults().get(0);
            assertThat(taskResult.taskId()).isEqualTo("implement");
            assertThat(taskResult.attempts()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("3.13 task ordering")
    class TaskOrderingTests {

        @Test
        @DisplayName("tasks execute in order of getOrder()")
        void tasksExecuteInOrderOfGetOrder() throws IOException {
            // Given
            List<String> executionOrder = new ArrayList<>();
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Task task300 = createTrackingTask("task300", 300, true, executionOrder);
            Task task100 = createTrackingTask("task100", 100, true, executionOrder);
            Task task200 = createTrackingTask("task200", 200, true, executionOrder);

            TaskPipeline pipeline = new TaskPipeline(
                List.of(task300, task100, task200), contextBuilder, backupManager, 3, implFile
            );

            // When
            pipeline.execute(sampleRequest);

            // Then - tasks should execute in order 100, 200, 300
            assertThat(executionOrder).containsExactly("task100", "task200", "task300");
        }
    }

    @Nested
    @DisplayName("3.14 skip logic")
    class SkipLogicTests {

        @Test
        @DisplayName("task is skipped when shouldRun() returns false")
        void taskIsSkippedWhenShouldRunReturnsFalse() throws IOException {
            // Given
            List<String> executionOrder = new ArrayList<>();
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Task runningTask = createTrackingTask("running", 100, true, executionOrder);
            Task skippedTask = createSkippingTask("skipped", 200, true);

            TaskPipeline pipeline = new TaskPipeline(
                List.of(runningTask, skippedTask), contextBuilder, backupManager, 3, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(executionOrder).containsExactly("running");

            TaskResult skippedResult = result.taskResults().stream()
                .filter(r -> r.taskId().equals("skipped"))
                .findFirst()
                .orElseThrow();
            assertThat(skippedResult.status()).isEqualTo(TaskStatus.SKIPPED);
        }
    }

    @Nested
    @DisplayName("3.15 required task failure")
    class RequiredTaskFailureTests {

        @Test
        @DisplayName("pipeline fails when required task fails")
        void pipelineFailsWhenRequiredTaskFails() throws IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Files.writeString(implFile, "original content");

            Task failingTask = createFailingTask("implement", 100, true);
            TaskPipeline pipeline = new TaskPipeline(
                List.of(failingTask), contextBuilder, backupManager, 0, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.taskResults().get(0).status()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        @DisplayName("files are reverted when required task fails")
        void filesAreRevertedWhenRequiredTaskFails() throws IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            String originalContent = "original content";
            Files.writeString(implFile, originalContent);

            // Task that modifies file then fails
            Task modifyingTask = new Task() {
                @Override
                public String getId() { return "modify-then-fail"; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    try {
                        Files.writeString(implFile, "modified content");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return new TaskResult("modify-then-fail", TaskStatus.FAILED, "failed", 1);
                }
            };

            TaskPipeline pipeline = new TaskPipeline(
                List.of(modifyingTask), contextBuilder, backupManager, 0, implFile
            );

            // When
            pipeline.execute(sampleRequest);

            // Then - file should be reverted
            assertThat(Files.readString(implFile)).isEqualTo(originalContent);
        }
    }

    @Nested
    @DisplayName("3.16 optional task failure")
    class OptionalTaskFailureTests {

        @Test
        @DisplayName("optional task failure is marked as REVERTED")
        void optionalTaskFailureIsMarkedAsReverted() throws IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Task successTask = createSuccessTask("implement", 100, true);
            Task optionalFailingTask = createFailingTask("refactor", 200, false);
            Task afterTask = createSuccessTask("format", 300, false);

            TaskPipeline pipeline = new TaskPipeline(
                List.of(successTask, optionalFailingTask, afterTask),
                contextBuilder, backupManager, 0, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then - pipeline succeeds, optional task is REVERTED
            assertThat(result.success()).isTrue();

            TaskResult revertedResult = result.taskResults().stream()
                .filter(r -> r.taskId().equals("refactor"))
                .findFirst()
                .orElseThrow();
            assertThat(revertedResult.status()).isEqualTo(TaskStatus.REVERTED);
        }

        @Test
        @DisplayName("pipeline continues after optional task failure")
        void pipelineContinuesAfterOptionalTaskFailure() throws IOException {
            // Given
            List<String> executionOrder = new ArrayList<>();
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Task firstTask = createTrackingTask("first", 100, true, executionOrder);
            Task optionalFailing = createTrackingFailingTask("optional", 200, false, executionOrder);
            Task lastTask = createTrackingTask("last", 300, false, executionOrder);

            TaskPipeline pipeline = new TaskPipeline(
                List.of(firstTask, optionalFailing, lastTask),
                contextBuilder, backupManager, 0, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(executionOrder).containsExactly("first", "optional", "last");
        }

        @Test
        @DisplayName("optional task changes are reverted on failure")
        void optionalTaskChangesAreRevertedOnFailure() throws IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            String originalContent = "original content";
            Files.writeString(implFile, originalContent);

            // First task succeeds and modifies file
            Task successTask = new Task() {
                @Override
                public String getId() { return "success"; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    try {
                        Files.writeString(implFile, "after success");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return new TaskResult("success", TaskStatus.SUCCESS, null, 1);
                }
            };

            // Optional task modifies then fails
            Task optionalTask = new Task() {
                @Override
                public String getId() { return "optional"; }
                @Override
                public int getOrder() { return 200; }
                @Override
                public boolean isRequired() { return false; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    try {
                        Files.writeString(implFile, "after optional");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return new TaskResult("optional", TaskStatus.FAILED, "failed", 1);
                }
            };

            TaskPipeline pipeline = new TaskPipeline(
                List.of(successTask, optionalTask),
                contextBuilder, backupManager, 0, implFile
            );

            // When
            pipeline.execute(sampleRequest);

            // Then - file should be reverted to state after success task
            assertThat(Files.readString(implFile)).isEqualTo("after success");
        }
    }

    @Nested
    @DisplayName("3.17 retry logic")
    class RetryLogicTests {

        @Test
        @DisplayName("required task is retried up to maxRetries")
        void requiredTaskIsRetriedUpToMaxRetries() throws IOException {
            // Given
            AtomicInteger attemptCount = new AtomicInteger(0);
            Path implFile = tempDir.resolve("src/main/java/Impl.java");

            // Task that fails twice then succeeds
            Task retryableTask = new Task() {
                @Override
                public String getId() { return "retryable"; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    int attempt = attemptCount.incrementAndGet();
                    if (attempt < 3) {
                        return new TaskResult("retryable", TaskStatus.FAILED, "attempt " + attempt, attempt);
                    }
                    return new TaskResult("retryable", TaskStatus.SUCCESS, null, attempt);
                }
            };

            TaskPipeline pipeline = new TaskPipeline(
                List.of(retryableTask), contextBuilder, backupManager, 5, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("task fails after exhausting retries")
        void taskFailsAfterExhaustingRetries() throws IOException {
            // Given
            AtomicInteger attemptCount = new AtomicInteger(0);
            Path implFile = tempDir.resolve("src/main/java/Impl.java");

            // Task that always fails
            Task alwaysFailsTask = new Task() {
                @Override
                public String getId() { return "always-fails"; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    int attempt = attemptCount.incrementAndGet();
                    return new TaskResult("always-fails", TaskStatus.FAILED, "attempt " + attempt, attempt);
                }
            };

            TaskPipeline pipeline = new TaskPipeline(
                List.of(alwaysFailsTask), contextBuilder, backupManager, 3, implFile
            );

            // When
            PipelineResult result = pipeline.execute(sampleRequest);

            // Then
            assertThat(result.success()).isFalse();
            // Should have tried: initial + 3 retries = 4 attempts (or 1 + maxRetries depending on interpretation)
            // With maxRetries=3, canRetry is true for attempts 1,2,3, false for 4
            assertThat(attemptCount.get()).isEqualTo(4);
        }
    }

    // Helper methods for creating test tasks

    private Task createSuccessTask(String id, int order, boolean required) {
        return new Task() {
            @Override
            public String getId() { return id; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean isRequired() { return required; }
            @Override
            public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
            @Override
            public TaskResult execute(ExtractionContext extraction, Object state) {
                return new TaskResult(id, TaskStatus.SUCCESS, null, 1);
            }
        };
    }

    private Task createFailingTask(String id, int order, boolean required) {
        return new Task() {
            @Override
            public String getId() { return id; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean isRequired() { return required; }
            @Override
            public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
            @Override
            public TaskResult execute(ExtractionContext extraction, Object state) {
                return new TaskResult(id, TaskStatus.FAILED, "failed", 1);
            }
        };
    }

    private Task createTrackingTask(String id, int order, boolean required, List<String> tracker) {
        return new Task() {
            @Override
            public String getId() { return id; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean isRequired() { return required; }
            @Override
            public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
            @Override
            public TaskResult execute(ExtractionContext extraction, Object state) {
                tracker.add(id);
                return new TaskResult(id, TaskStatus.SUCCESS, null, 1);
            }
        };
    }

    private Task createTrackingFailingTask(String id, int order, boolean required, List<String> tracker) {
        return new Task() {
            @Override
            public String getId() { return id; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean isRequired() { return required; }
            @Override
            public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
            @Override
            public TaskResult execute(ExtractionContext extraction, Object state) {
                tracker.add(id);
                return new TaskResult(id, TaskStatus.FAILED, "failed", 1);
            }
        };
    }

    private Task createSkippingTask(String id, int order, boolean required) {
        return new Task() {
            @Override
            public String getId() { return id; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean isRequired() { return required; }
            @Override
            public boolean shouldRun(ExtractionContext extraction, Object state) { return false; }
            @Override
            public TaskResult execute(ExtractionContext extraction, Object state) {
                throw new IllegalStateException("Should not be called");
            }
        };
    }
}
