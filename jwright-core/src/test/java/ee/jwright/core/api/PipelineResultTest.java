package ee.jwright.core.api;

import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PipelineResult and InitResult records.
 */
@DisplayName("Pipeline Results")
class PipelineResultTest {

    @Nested
    @DisplayName("PipelineResult record")
    class PipelineResultTests {

        @Test
        @DisplayName("should create successful result with all fields")
        void shouldCreateSuccessfulResult() {
            var taskResults = List.of(
                new TaskResult("implement", TaskStatus.SUCCESS, "Generated code", 2),
                new TaskResult("refactor", TaskStatus.SUCCESS, "Refactored", 1)
            );
            var modifiedFile = Path.of("/project/src/main/java/Foo.java");
            var finalCode = "public void foo() { return 42; }";

            var result = new PipelineResult(true, taskResults, modifiedFile, finalCode);

            assertThat(result.success()).isTrue();
            assertThat(result.taskResults()).hasSize(2);
            assertThat(result.modifiedFile()).isEqualTo(modifiedFile);
            assertThat(result.finalCode()).isEqualTo(finalCode);
        }

        @Test
        @DisplayName("should create failed result with null file and code")
        void shouldCreateFailedResult() {
            var taskResults = List.of(
                new TaskResult("implement", TaskStatus.FAILED, "Max retries exceeded", 5)
            );

            var result = new PipelineResult(false, taskResults, null, null);

            assertThat(result.success()).isFalse();
            assertThat(result.modifiedFile()).isNull();
            assertThat(result.finalCode()).isNull();
        }

        @Test
        @DisplayName("hasWarnings should return false when no reverted tasks")
        void hasWarningsShouldReturnFalseWhenNoRevertedTasks() {
            var taskResults = List.of(
                new TaskResult("implement", TaskStatus.SUCCESS, "done", 1),
                new TaskResult("refactor", TaskStatus.SUCCESS, "done", 1)
            );

            var result = new PipelineResult(true, taskResults, null, null);

            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("hasWarnings should return true when any task is reverted")
        void hasWarningsShouldReturnTrueWhenAnyTaskReverted() {
            var taskResults = List.of(
                new TaskResult("implement", TaskStatus.SUCCESS, "done", 1),
                new TaskResult("refactor", TaskStatus.REVERTED, "broke tests, reverted", 2)
            );

            var result = new PipelineResult(true, taskResults, null, null);

            assertThat(result.hasWarnings()).isTrue();
        }

        @Test
        @DisplayName("hasWarnings should return false for empty task list")
        void hasWarningsShouldReturnFalseForEmptyTaskList() {
            var result = new PipelineResult(true, List.of(), null, null);

            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("hasWarnings should return false for skipped tasks")
        void hasWarningsShouldReturnFalseForSkippedTasks() {
            var taskResults = List.of(
                new TaskResult("implement", TaskStatus.SUCCESS, "done", 1),
                new TaskResult("refactor", TaskStatus.SKIPPED, null, 0)
            );

            var result = new PipelineResult(true, taskResults, null, null);

            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("should implement record equality")
        void shouldImplementRecordEquality() {
            var taskResults = List.of(new TaskResult("implement", TaskStatus.SUCCESS, "done", 1));
            var path = Path.of("/foo/bar.java");

            var result1 = new PipelineResult(true, taskResults, path, "code");
            var result2 = new PipelineResult(true, taskResults, path, "code");

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }
    }

    @Nested
    @DisplayName("InitResult record")
    class InitResultTests {

        @Test
        @DisplayName("should create result with config file and templates dir")
        void shouldCreateResultWithConfigAndTemplates() {
            var configFile = Path.of("/project/.jwright/config.yaml");
            var templatesDir = Path.of("/project/.jwright/templates");

            var result = new InitResult(configFile, templatesDir);

            assertThat(result.configFile()).isEqualTo(configFile);
            assertThat(result.templatesDir()).isEqualTo(templatesDir);
        }

        @Test
        @DisplayName("should implement record equality")
        void shouldImplementRecordEquality() {
            var configFile = Path.of("/project/.jwright/config.yaml");
            var templatesDir = Path.of("/project/.jwright/templates");

            var result1 = new InitResult(configFile, templatesDir);
            var result2 = new InitResult(configFile, templatesDir);

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("should have useful toString")
        void shouldHaveUsefulToString() {
            var configFile = Path.of("/project/.jwright/config.yaml");
            var templatesDir = Path.of("/project/.jwright/templates");

            var result = new InitResult(configFile, templatesDir);

            assertThat(result.toString())
                .contains("config.yaml")
                .contains("templates");
        }
    }
}
