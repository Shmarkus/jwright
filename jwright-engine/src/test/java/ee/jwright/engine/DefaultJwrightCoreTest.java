package ee.jwright.engine;

import ee.jwright.core.api.*;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.exception.JwrightException;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.engine.context.ContextBuilder;
import ee.jwright.engine.pipeline.BackupManager;
import ee.jwright.engine.pipeline.TaskPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultJwrightCore}.
 */
class DefaultJwrightCoreTest {

    @Nested
    @DisplayName("3.18 init")
    class InitTests {

        @Test
        @DisplayName("init() creates .jwright/config.yaml")
        void initCreatesConfigFile(@TempDir Path tempDir) throws JwrightException {
            // Given
            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                Collections.emptyList(),
                5
            );

            // When
            InitResult result = core.init(tempDir);

            // Then
            assertThat(result.configFile()).isEqualTo(tempDir.resolve(".jwright/config.yaml"));
            assertThat(Files.exists(result.configFile())).isTrue();
        }

        @Test
        @DisplayName("init() creates .jwright/templates/ directory")
        void initCreatesTemplatesDirectory(@TempDir Path tempDir) throws JwrightException {
            // Given
            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                Collections.emptyList(),
                5
            );

            // When
            InitResult result = core.init(tempDir);

            // Then
            assertThat(result.templatesDir()).isEqualTo(tempDir.resolve(".jwright/templates"));
            assertThat(Files.isDirectory(result.templatesDir())).isTrue();
        }

        @Test
        @DisplayName("init() config.yaml has sensible defaults")
        void initConfigHasSensibleDefaults(@TempDir Path tempDir) throws JwrightException, IOException {
            // Given
            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                Collections.emptyList(),
                5
            );

            // When
            InitResult result = core.init(tempDir);

            // Then
            String configContent = Files.readString(result.configFile());
            assertThat(configContent).contains("jwright:");
            assertThat(configContent).contains("llm:");
            assertThat(configContent).contains("provider:");
        }

        @Test
        @DisplayName("init() is idempotent")
        void initIsIdempotent(@TempDir Path tempDir) throws JwrightException {
            // Given
            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                Collections.emptyList(),
                5
            );

            // When
            InitResult result1 = core.init(tempDir);
            InitResult result2 = core.init(tempDir);

            // Then
            assertThat(result1).isEqualTo(result2);
            assertThat(Files.exists(result1.configFile())).isTrue();
        }
    }

    @Nested
    @DisplayName("3.19 implement")
    class ImplementTests {

        @Test
        @DisplayName("implement() wires together ContextBuilder and TaskPipeline")
        void implementWiresTogetherComponents(@TempDir Path tempDir) throws JwrightException, IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Files.createDirectories(implFile.getParent());
            Files.writeString(implFile, "public class Impl {}");

            // Create a simple task that succeeds
            Task successTask = new Task() {
                @Override
                public String getId() { return "test-task"; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    return new TaskResult("test-task", TaskStatus.SUCCESS, "executed", 1);
                }
            };

            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                List.of(successTask),
                5
            );

            ImplementRequest request = new ImplementRequest(
                tempDir, "Test#testMethod", false, LogLevel.INFO
            );

            // When
            PipelineResult result = core.implement(request);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.taskResults()).hasSize(1);
            assertThat(result.taskResults().get(0).taskId()).isEqualTo("test-task");
        }

        @Test
        @DisplayName("implement() returns failed result when task fails")
        void implementReturnsFailedResult(@TempDir Path tempDir) throws JwrightException, IOException {
            // Given
            Path implFile = tempDir.resolve("src/main/java/Impl.java");
            Files.createDirectories(implFile.getParent());
            Files.writeString(implFile, "public class Impl {}");

            Task failingTask = new Task() {
                @Override
                public String getId() { return "failing-task"; }
                @Override
                public int getOrder() { return 100; }
                @Override
                public boolean isRequired() { return true; }
                @Override
                public boolean shouldRun(ExtractionContext extraction, Object state) { return true; }
                @Override
                public TaskResult execute(ExtractionContext extraction, Object state) {
                    return new TaskResult("failing-task", TaskStatus.FAILED, "failed", 1);
                }
            };

            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                List.of(failingTask),
                5
            );

            ImplementRequest request = new ImplementRequest(
                tempDir, "Test#testMethod", false, LogLevel.INFO
            );

            // When
            PipelineResult result = core.implement(request);

            // Then
            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("3.20 watch")
    class WatchTests {

        @Test
        @DisplayName("watch() returns a running watch handle")
        void watchReturnsRunningHandle(@TempDir Path tempDir) throws JwrightException {
            // Given
            BuildTool buildTool = mock(BuildTool.class);
            when(buildTool.runTests(anyString())).thenReturn(
                new TestResult(true, 1, 0, List.of())
            );

            DefaultJwrightCore core = new DefaultJwrightCore(
                new ContextBuilder(Collections.emptyList()),
                Collections.emptyList(),
                5,
                null,
                null,
                null,
                buildTool
            );

            WatchRequest request = new WatchRequest(
                tempDir,
                List.of(tempDir),
                List.of(),
                Duration.ofMillis(100),
                LogLevel.INFO
            );

            WatchCallback callback = new WatchCallback() {
                @Override
                public void onFileChanged(Path file) {}
                @Override
                public void onTestDetected(String testTarget) {}
                @Override
                public void onGenerationStarted(String testTarget) {}
                @Override
                public void onGenerationComplete(PipelineResult result) {}
                @Override
                public void onError(JwrightException error) {}
            };

            // When
            WatchHandle handle = core.watch(request, callback);

            // Then
            assertThat(handle.isRunning()).isTrue();
            assertThat(handle.getWatchedDirectory()).isEqualTo(tempDir);

            // Cleanup
            handle.stop();
            assertThat(handle.isRunning()).isFalse();
        }
    }
}
