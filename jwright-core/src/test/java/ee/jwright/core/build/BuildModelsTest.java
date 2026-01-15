package ee.jwright.core.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for build models.
 */
@DisplayName("Build Models")
class BuildModelsTest {

    @Nested
    @DisplayName("CompilationError record")
    class CompilationErrorTests {

        @Test
        @DisplayName("should create error with all fields")
        void shouldCreateErrorWithAllFields() {
            var error = new CompilationError(
                Path.of("/project/src/main/java/Foo.java"),
                42,
                "';' expected"
            );

            assertThat(error.file()).isEqualTo(Path.of("/project/src/main/java/Foo.java"));
            assertThat(error.line()).isEqualTo(42);
            assertThat(error.message()).isEqualTo("';' expected");
        }
    }

    @Nested
    @DisplayName("CompilationResult record")
    class CompilationResultTests {

        @Test
        @DisplayName("should create successful result with empty errors")
        void shouldCreateSuccessfulResult() {
            var result = new CompilationResult(true, List.of());

            assertThat(result.success()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("should create failed result with errors")
        void shouldCreateFailedResult() {
            var errors = List.of(
                new CompilationError(Path.of("Foo.java"), 10, "missing return"),
                new CompilationError(Path.of("Foo.java"), 15, "type mismatch")
            );

            var result = new CompilationResult(false, errors);

            assertThat(result.success()).isFalse();
            assertThat(result.errors()).hasSize(2);
            assertThat(result.errors().get(0).message()).isEqualTo("missing return");
        }
    }

    @Nested
    @DisplayName("TestFailure record")
    class TestFailureTests {

        @Test
        @DisplayName("should create failure with all fields")
        void shouldCreateFailureWithAllFields() {
            var failure = new TestFailure(
                "com.example.FooTest",
                "testAdd",
                "expected: <5> but was: <4>",
                "java.lang.AssertionError: expected: <5> but was: <4>\n\tat ..."
            );

            assertThat(failure.testClass()).isEqualTo("com.example.FooTest");
            assertThat(failure.testMethod()).isEqualTo("testAdd");
            assertThat(failure.message()).contains("expected: <5>");
            assertThat(failure.stackTrace()).contains("AssertionError");
        }

        @Test
        @DisplayName("should allow null stack trace")
        void shouldAllowNullStackTrace() {
            var failure = new TestFailure(
                "FooTest",
                "testBar",
                "Test timed out",
                null
            );

            assertThat(failure.stackTrace()).isNull();
        }
    }

    @Nested
    @DisplayName("TestResult record")
    class TestResultTests {

        @Test
        @DisplayName("should create successful result with passed count")
        void shouldCreateSuccessfulResult() {
            var result = new TestResult(true, 10, 0, List.of());

            assertThat(result.success()).isTrue();
            assertThat(result.passed()).isEqualTo(10);
            assertThat(result.failed()).isZero();
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("should create failed result with failures")
        void shouldCreateFailedResult() {
            var failures = List.of(
                new TestFailure("FooTest", "testAdd", "wrong result", null),
                new TestFailure("FooTest", "testSubtract", "wrong result", null)
            );

            var result = new TestResult(false, 8, 2, failures);

            assertThat(result.success()).isFalse();
            assertThat(result.passed()).isEqualTo(8);
            assertThat(result.failed()).isEqualTo(2);
            assertThat(result.failures()).hasSize(2);
        }

        @Test
        @DisplayName("should calculate total tests from passed and failed")
        void shouldCalculateTotalTests() {
            var result = new TestResult(true, 15, 5, List.of());

            assertThat(result.passed() + result.failed()).isEqualTo(20);
        }
    }
}
