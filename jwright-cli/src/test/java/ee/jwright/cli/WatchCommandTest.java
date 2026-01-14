package ee.jwright.cli;

import ee.jwright.core.api.JwrightCore;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.api.WatchHandle;
import ee.jwright.core.api.WatchRequest;
import ee.jwright.core.exception.JwrightException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WatchCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("9.4 WatchCommand")
class WatchCommandTest {

    @Mock
    private JwrightCore core;

    private WatchCommand command;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new WatchCommand(core);
    }

    @Nested
    @DisplayName("Command configuration")
    class CommandConfigurationTests {

        @Test
        @DisplayName("has correct command name")
        void hasCorrectCommandName() {
            var annotation = WatchCommand.class.getAnnotation(picocli.CommandLine.Command.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.name()).isEqualTo("watch");
        }

        @Test
        @DisplayName("has description")
        void hasDescription() {
            var annotation = WatchCommand.class.getAnnotation(picocli.CommandLine.Command.class);
            assertThat(annotation.description()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Watch execution")
    class WatchExecutionTests {

        @Test
        @DisplayName("starts watch with correct parameters")
        void startsWatchWithCorrectParameters() throws Exception {
            // Given
            WatchHandle mockHandle = mock(WatchHandle.class);
            when(mockHandle.isRunning()).thenReturn(false); // Immediately stop
            when(mockHandle.getWatchedDirectory()).thenReturn(tempDir);
            when(core.watch(any(WatchRequest.class), any(WatchCallback.class))).thenReturn(mockHandle);

            command.setProjectDir(tempDir);
            command.setTimeout(Duration.ofMillis(100));

            // When
            Integer result = command.call();

            // Then
            verify(core).watch(any(WatchRequest.class), any(WatchCallback.class));
            assertThat(result).isEqualTo(ExitCode.SUCCESS);
        }

        @Test
        @DisplayName("reports errors when watch fails")
        void reportsErrorsWhenWatchFails() throws Exception {
            // Given
            when(core.watch(any(WatchRequest.class), any(WatchCallback.class)))
                .thenThrow(new JwrightException(
                    JwrightException.ErrorCode.CONFIG_INVALID,
                    "Watch failed"
                ));

            command.setProjectDir(tempDir);

            // When
            Integer result = command.call();

            // Then
            assertThat(result).isEqualTo(ExitCode.CONFIG_ERROR);
        }
    }

    @Nested
    @DisplayName("Callback handling")
    class CallbackHandlingTests {

        @Test
        @DisplayName("callback receives file change notifications")
        void callbackReceivesFileChangeNotifications() throws Exception {
            // Given
            AtomicBoolean fileChangeCalled = new AtomicBoolean(false);
            WatchHandle mockHandle = mock(WatchHandle.class);
            when(mockHandle.isRunning()).thenReturn(false);

            when(core.watch(any(WatchRequest.class), any(WatchCallback.class)))
                .thenAnswer(invocation -> {
                    WatchCallback callback = invocation.getArgument(1);
                    // Simulate a file change
                    callback.onFileChanged(tempDir.resolve("Test.java"));
                    fileChangeCalled.set(true);
                    return mockHandle;
                });

            command.setProjectDir(tempDir);
            command.setTimeout(Duration.ofMillis(100));

            // When
            command.call();

            // Then
            assertThat(fileChangeCalled.get()).isTrue();
        }

        @Test
        @DisplayName("callback receives generation started notification")
        void callbackReceivesGenerationStartedNotification() throws Exception {
            // Given
            AtomicBoolean generationStarted = new AtomicBoolean(false);
            WatchHandle mockHandle = mock(WatchHandle.class);
            when(mockHandle.isRunning()).thenReturn(false);

            when(core.watch(any(WatchRequest.class), any(WatchCallback.class)))
                .thenAnswer(invocation -> {
                    WatchCallback callback = invocation.getArgument(1);
                    callback.onGenerationStarted("TestClass#testMethod");
                    generationStarted.set(true);
                    return mockHandle;
                });

            command.setProjectDir(tempDir);
            command.setTimeout(Duration.ofMillis(100));

            // When
            command.call();

            // Then
            assertThat(generationStarted.get()).isTrue();
        }
    }
}
