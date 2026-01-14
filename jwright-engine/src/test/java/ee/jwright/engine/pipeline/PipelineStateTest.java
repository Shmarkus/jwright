package ee.jwright.engine.pipeline;

import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PipelineState}.
 */
class PipelineStateTest {

    @Nested
    @DisplayName("3.4 basic attempt tracking")
    class BasicAttemptTrackingTests {

        @Test
        @DisplayName("initial attempt number should be 1")
        void initialAttemptNumberShouldBeOne() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.getAttemptNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("incrementAttempt() should increase attempt number")
        void incrementAttemptShouldIncreaseAttemptNumber() {
            // Given
            PipelineState state = new PipelineState(3);

            // When
            state.incrementAttempt();

            // Then
            assertThat(state.getAttemptNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("canRetry() should return true when attempts remain")
        void canRetryShouldReturnTrueWhenAttemptsRemain() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.canRetry()).isTrue();
        }

        @Test
        @DisplayName("canRetry() should return false when max retries reached")
        void canRetryShouldReturnFalseWhenMaxRetriesReached() {
            // Given
            PipelineState state = new PipelineState(3);

            // When
            state.incrementAttempt(); // attempt 2
            state.incrementAttempt(); // attempt 3
            state.incrementAttempt(); // attempt 4 (exceeds max)

            // Then
            assertThat(state.canRetry()).isFalse();
        }

        @Test
        @DisplayName("getMaxRetries() should return configured value")
        void getMaxRetriesShouldReturnConfiguredValue() {
            // Given
            PipelineState state = new PipelineState(5);

            // Then
            assertThat(state.getMaxRetries()).isEqualTo(5);
        }

        @Test
        @DisplayName("canRetry() with 0 maxRetries means no retries allowed")
        void canRetryWithZeroMaxRetriesMeansNoRetriesAllowed() {
            // Given
            PipelineState state = new PipelineState(0);

            // Then - first attempt is 1, 0 retries means only 1 attempt total
            assertThat(state.canRetry()).isFalse();
        }
    }

    @Nested
    @DisplayName("3.5 failure tracking")
    class FailureTrackingTests {

        @Test
        @DisplayName("hasFailures() should return false initially")
        void hasFailuresShouldReturnFalseInitially() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.hasFailures()).isFalse();
        }

        @Test
        @DisplayName("recordFailure() should track failed attempt")
        void recordFailureShouldTrackFailedAttempt() {
            // Given
            PipelineState state = new PipelineState(3);
            FailedAttempt failure = new FailedAttempt(
                1,
                "generated code",
                "error message",
                null,
                null
            );

            // When
            state.recordFailure(failure);

            // Then
            assertThat(state.hasFailures()).isTrue();
            assertThat(state.getFailedAttempts()).hasSize(1);
            assertThat(state.getFailedAttempts().get(0)).isEqualTo(failure);
        }

        @Test
        @DisplayName("getFailedAttempts() should return all recorded failures")
        void getFailedAttemptsShouldReturnAllRecordedFailures() {
            // Given
            PipelineState state = new PipelineState(3);
            FailedAttempt failure1 = new FailedAttempt(
                1, "code1", "error1",
                new CompilationError(Path.of("Test.java"), 10, "syntax error"),
                null
            );
            FailedAttempt failure2 = new FailedAttempt(
                2, "code2", "error2",
                null,
                new TestFailure("TestClass", "testMethod", "assertion failed", "stack")
            );

            // When
            state.recordFailure(failure1);
            state.recordFailure(failure2);

            // Then
            assertThat(state.getFailedAttempts()).hasSize(2);
            assertThat(state.getFailedAttempts()).containsExactly(failure1, failure2);
        }

        @Test
        @DisplayName("getFailedAttempts() should return empty list initially")
        void getFailedAttemptsShouldReturnEmptyListInitially() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.getFailedAttempts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("3.6 code and task tracking")
    class CodeAndTaskTrackingTests {

        @Test
        @DisplayName("setGeneratedCode() and getGeneratedCode() should store and retrieve code")
        void setAndGetGeneratedCode() {
            // Given
            PipelineState state = new PipelineState(3);
            String generatedCode = "return x + y;";

            // When
            state.setGeneratedCode(generatedCode);

            // Then
            assertThat(state.getGeneratedCode()).isEqualTo(generatedCode);
        }

        @Test
        @DisplayName("getGeneratedCode() should return null initially")
        void getGeneratedCodeShouldReturnNullInitially() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.getGeneratedCode()).isNull();
        }

        @Test
        @DisplayName("setCurrentTask() and getCurrentTask() should store and retrieve task")
        void setAndGetCurrentTask() {
            // Given
            PipelineState state = new PipelineState(3);
            Task mockTask = createMockTask("implement");

            // When
            state.setCurrentTask(mockTask);

            // Then
            assertThat(state.getCurrentTask()).isSameAs(mockTask);
        }

        @Test
        @DisplayName("getCurrentTask() should return null initially")
        void getCurrentTaskShouldReturnNullInitially() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.getCurrentTask()).isNull();
        }

        @Test
        @DisplayName("setLastTaskStatus() and getLastTaskStatus() should store and retrieve status")
        void setAndGetLastTaskStatus() {
            // Given
            PipelineState state = new PipelineState(3);

            // When
            state.setLastTaskStatus(TaskStatus.SUCCESS);

            // Then
            assertThat(state.getLastTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
        }

        @Test
        @DisplayName("getLastTaskStatus() should return null initially")
        void getLastTaskStatusShouldReturnNullInitially() {
            // Given
            PipelineState state = new PipelineState(3);

            // Then
            assertThat(state.getLastTaskStatus()).isNull();
        }

        private Task createMockTask(String id) {
            return new Task() {
                @Override
                public String getId() { return id; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    return new TaskResult(id, TaskStatus.SUCCESS, null, 1);
                }
            };
        }
    }
}
