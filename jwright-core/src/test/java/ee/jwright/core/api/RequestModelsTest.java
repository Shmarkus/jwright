package ee.jwright.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for request models (LogLevel, ImplementRequest, WatchRequest).
 */
@DisplayName("Request Models")
class RequestModelsTest {

    @Nested
    @DisplayName("LogLevel enum")
    class LogLevelTests {

        @Test
        @DisplayName("should have all required log levels")
        void shouldHaveAllRequiredLogLevels() {
            assertThat(LogLevel.values())
                .containsExactlyInAnyOrder(
                    LogLevel.QUIET,
                    LogLevel.INFO,
                    LogLevel.DEBUG,
                    LogLevel.TRACE
                );
        }

        @Test
        @DisplayName("QUIET - minimal output (warnings and errors only)")
        void quietLevel() {
            assertThat(LogLevel.QUIET.name()).isEqualTo("QUIET");
        }

        @Test
        @DisplayName("INFO - standard progress output")
        void infoLevel() {
            assertThat(LogLevel.INFO.name()).isEqualTo("INFO");
        }

        @Test
        @DisplayName("DEBUG - verbose output with prompts and responses")
        void debugLevel() {
            assertThat(LogLevel.DEBUG.name()).isEqualTo("DEBUG");
        }

        @Test
        @DisplayName("TRACE - everything (development only)")
        void traceLevel() {
            assertThat(LogLevel.TRACE.name()).isEqualTo("TRACE");
        }
    }

    @Nested
    @DisplayName("ImplementRequest record")
    class ImplementRequestTests {

        @Test
        @DisplayName("should create request with all fields")
        void shouldCreateRequestWithAllFields() {
            var projectDir = Path.of("/home/user/project");
            var target = "FooTest#testBar";

            var request = new ImplementRequest(projectDir, target, false, LogLevel.INFO);

            assertThat(request.projectDir()).isEqualTo(projectDir);
            assertThat(request.target()).isEqualTo(target);
            assertThat(request.dryRun()).isFalse();
            assertThat(request.logLevel()).isEqualTo(LogLevel.INFO);
        }

        @Test
        @DisplayName("should create dry run request")
        void shouldCreateDryRunRequest() {
            var projectDir = Path.of("/home/user/project");
            var target = "FooTest#testBar";

            var request = new ImplementRequest(projectDir, target, true, LogLevel.DEBUG);

            assertThat(request.dryRun()).isTrue();
            assertThat(request.logLevel()).isEqualTo(LogLevel.DEBUG);
        }

        @Test
        @DisplayName("should implement record equality")
        void shouldImplementRecordEquality() {
            var projectDir = Path.of("/home/user/project");

            var request1 = new ImplementRequest(projectDir, "FooTest#testBar", false, LogLevel.INFO);
            var request2 = new ImplementRequest(projectDir, "FooTest#testBar", false, LogLevel.INFO);

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should have useful toString")
        void shouldHaveUsefulToString() {
            var projectDir = Path.of("/home/user/project");
            var request = new ImplementRequest(projectDir, "FooTest#testBar", true, LogLevel.DEBUG);

            assertThat(request.toString())
                .contains("project")
                .contains("FooTest#testBar")
                .contains("true")
                .contains("DEBUG");
        }
    }

    @Nested
    @DisplayName("WatchRequest record")
    class WatchRequestTests {

        @Test
        @DisplayName("should create request with all fields")
        void shouldCreateRequestWithAllFields() {
            var projectDir = Path.of("/home/user/project");
            var watchPaths = List.of(
                Path.of("src/test/java"),
                Path.of("src/main/java")
            );
            var ignorePatterns = List.of("**/*.class", "**/target/**");
            var debounce = Duration.ofMillis(500);

            var request = new WatchRequest(projectDir, watchPaths, ignorePatterns, debounce, LogLevel.INFO);

            assertThat(request.projectDir()).isEqualTo(projectDir);
            assertThat(request.watchPaths()).hasSize(2);
            assertThat(request.ignorePatterns()).containsExactly("**/*.class", "**/target/**");
            assertThat(request.debounce()).isEqualTo(Duration.ofMillis(500));
            assertThat(request.logLevel()).isEqualTo(LogLevel.INFO);
        }

        @Test
        @DisplayName("should allow empty watch paths (use defaults)")
        void shouldAllowEmptyWatchPaths() {
            var projectDir = Path.of("/home/user/project");

            var request = new WatchRequest(
                projectDir,
                List.of(),
                List.of(),
                Duration.ofMillis(300),
                LogLevel.QUIET
            );

            assertThat(request.watchPaths()).isEmpty();
            assertThat(request.ignorePatterns()).isEmpty();
        }

        @Test
        @DisplayName("should implement record equality")
        void shouldImplementRecordEquality() {
            var projectDir = Path.of("/home/user/project");
            var watchPaths = List.of(Path.of("src/test/java"));
            var ignorePatterns = List.of("**/*.class");
            var debounce = Duration.ofMillis(500);

            var request1 = new WatchRequest(projectDir, watchPaths, ignorePatterns, debounce, LogLevel.INFO);
            var request2 = new WatchRequest(projectDir, watchPaths, ignorePatterns, debounce, LogLevel.INFO);

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should have useful toString")
        void shouldHaveUsefulToString() {
            var projectDir = Path.of("/home/user/project");
            var watchPaths = List.of(Path.of("src/test/java"));
            var debounce = Duration.ofMillis(500);

            var request = new WatchRequest(projectDir, watchPaths, List.of(), debounce, LogLevel.INFO);

            assertThat(request.toString())
                .contains("project")
                .contains("test/java");
        }
    }
}
