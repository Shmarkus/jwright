package ee.jwright.core.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Task-related models (TaskStatus and TaskResult).
 */
@DisplayName("Task Models")
class TaskModelsTest {

    @Nested
    @DisplayName("TaskStatus enum")
    class TaskStatusTests {

        @Test
        @DisplayName("should have all required status values")
        void shouldHaveAllRequiredStatusValues() {
            assertThat(TaskStatus.values())
                .containsExactlyInAnyOrder(
                    TaskStatus.SUCCESS,
                    TaskStatus.FAILED,
                    TaskStatus.SKIPPED,
                    TaskStatus.REVERTED
                );
        }

        @Test
        @DisplayName("SUCCESS - indicates task completed successfully")
        void successStatus() {
            assertThat(TaskStatus.SUCCESS.name()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("FAILED - indicates task failed and could not be recovered")
        void failedStatus() {
            assertThat(TaskStatus.FAILED.name()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("SKIPPED - indicates task was skipped based on shouldRun condition")
        void skippedStatus() {
            assertThat(TaskStatus.SKIPPED.name()).isEqualTo("SKIPPED");
        }

        @Test
        @DisplayName("REVERTED - indicates optional task failed and changes were reverted")
        void revertedStatus() {
            assertThat(TaskStatus.REVERTED.name()).isEqualTo("REVERTED");
        }
    }

    @Nested
    @DisplayName("TaskResult record")
    class TaskResultTests {

        @Test
        @DisplayName("should create result with all fields")
        void shouldCreateResultWithAllFields() {
            var result = new TaskResult("implement", TaskStatus.SUCCESS, "Generated 5 lines", 2);

            assertThat(result.taskId()).isEqualTo("implement");
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.message()).isEqualTo("Generated 5 lines");
            assertThat(result.attempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("should allow null message for skipped tasks")
        void shouldAllowNullMessage() {
            var result = new TaskResult("refactor", TaskStatus.SKIPPED, null, 0);

            assertThat(result.taskId()).isEqualTo("refactor");
            assertThat(result.status()).isEqualTo(TaskStatus.SKIPPED);
            assertThat(result.message()).isNull();
            assertThat(result.attempts()).isZero();
        }

        @Test
        @DisplayName("should implement equals based on all fields")
        void shouldImplementEquals() {
            var result1 = new TaskResult("implement", TaskStatus.SUCCESS, "done", 1);
            var result2 = new TaskResult("implement", TaskStatus.SUCCESS, "done", 1);
            var result3 = new TaskResult("implement", TaskStatus.FAILED, "done", 1);

            assertThat(result1).isEqualTo(result2);
            assertThat(result1).isNotEqualTo(result3);
        }

        @Test
        @DisplayName("should implement hashCode based on all fields")
        void shouldImplementHashCode() {
            var result1 = new TaskResult("implement", TaskStatus.SUCCESS, "done", 1);
            var result2 = new TaskResult("implement", TaskStatus.SUCCESS, "done", 1);

            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("should have useful toString")
        void shouldHaveUsefulToString() {
            var result = new TaskResult("implement", TaskStatus.SUCCESS, "done", 1);

            assertThat(result.toString())
                .contains("implement")
                .contains("SUCCESS")
                .contains("done")
                .contains("1");
        }

        @Test
        @DisplayName("should represent failed task with message")
        void shouldRepresentFailedTask() {
            var result = new TaskResult(
                "implement",
                TaskStatus.FAILED,
                "Compilation error: missing semicolon",
                5
            );

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.message()).contains("Compilation error");
            assertThat(result.attempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("should represent reverted optional task")
        void shouldRepresentRevertedTask() {
            var result = new TaskResult(
                "refactor",
                TaskStatus.REVERTED,
                "Refactoring broke tests, reverted to original",
                2
            );

            assertThat(result.status()).isEqualTo(TaskStatus.REVERTED);
            assertThat(result.message()).contains("reverted");
        }
    }
}
