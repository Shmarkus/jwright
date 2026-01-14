package ee.jwright.core.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LlmException.
 */
@DisplayName("LlmException")
class LlmExceptionTest {

    @Nested
    @DisplayName("ErrorCode enum")
    class ErrorCodeTests {

        @Test
        @DisplayName("should have all required error codes")
        void shouldHaveAllRequiredErrorCodes() {
            assertThat(LlmException.ErrorCode.values())
                .containsExactlyInAnyOrder(
                    LlmException.ErrorCode.TIMEOUT,
                    LlmException.ErrorCode.UNAVAILABLE,
                    LlmException.ErrorCode.RATE_LIMITED,
                    LlmException.ErrorCode.CONTEXT_EXCEEDED,
                    LlmException.ErrorCode.INVALID_RESPONSE,
                    LlmException.ErrorCode.UNKNOWN
                );
        }
    }

    @Nested
    @DisplayName("Exception creation")
    class ExceptionCreationTests {

        @Test
        @DisplayName("should create exception with code and message")
        void shouldCreateExceptionWithCodeAndMessage() {
            var exception = new LlmException(
                LlmException.ErrorCode.TIMEOUT,
                "Request timed out after 120 seconds"
            );

            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.TIMEOUT);
            assertThat(exception.getMessage()).isEqualTo("Request timed out after 120 seconds");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("should create exception with code, message, and cause")
        void shouldCreateExceptionWithCodeMessageAndCause() {
            var cause = new java.net.ConnectException("Connection refused");
            var exception = new LlmException(
                LlmException.ErrorCode.UNAVAILABLE,
                "Ollama server not running",
                cause
            );

            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.UNAVAILABLE);
            assertThat(exception.getMessage()).isEqualTo("Ollama server not running");
            assertThat(exception.getCause()).isEqualTo(cause);
        }

        @ParameterizedTest
        @EnumSource(LlmException.ErrorCode.class)
        @DisplayName("should be throwable with any error code")
        void shouldBeThrowableWithAnyErrorCode(LlmException.ErrorCode code) {
            assertThatThrownBy(() -> {
                throw new LlmException(code, "Test message for " + code);
            })
                .isInstanceOf(LlmException.class)
                .hasMessage("Test message for " + code)
                .satisfies(e -> assertThat(((LlmException) e).getCode()).isEqualTo(code));
        }
    }

    @Nested
    @DisplayName("Error scenarios")
    class ErrorScenarioTests {

        @Test
        @DisplayName("TIMEOUT - when LLM request times out")
        void timeoutScenario() {
            var exception = new LlmException(
                LlmException.ErrorCode.TIMEOUT,
                "Request to qwen2.5-coder:14b timed out after 120s"
            );
            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.TIMEOUT);
        }

        @Test
        @DisplayName("UNAVAILABLE - when LLM server is not reachable")
        void unavailableScenario() {
            var exception = new LlmException(
                LlmException.ErrorCode.UNAVAILABLE,
                "Cannot connect to Ollama at http://localhost:11434"
            );
            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.UNAVAILABLE);
        }

        @Test
        @DisplayName("RATE_LIMITED - when too many requests")
        void rateLimitedScenario() {
            var exception = new LlmException(
                LlmException.ErrorCode.RATE_LIMITED,
                "Rate limit exceeded. Try again in 60 seconds."
            );
            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.RATE_LIMITED);
        }

        @Test
        @DisplayName("CONTEXT_EXCEEDED - when prompt is too large")
        void contextExceededScenario() {
            var exception = new LlmException(
                LlmException.ErrorCode.CONTEXT_EXCEEDED,
                "Prompt exceeds model context window of 8192 tokens"
            );
            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.CONTEXT_EXCEEDED);
        }

        @Test
        @DisplayName("INVALID_RESPONSE - when LLM returns unparseable response")
        void invalidResponseScenario() {
            var exception = new LlmException(
                LlmException.ErrorCode.INVALID_RESPONSE,
                "Response did not contain valid code block"
            );
            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.INVALID_RESPONSE);
        }

        @Test
        @DisplayName("UNKNOWN - for unexpected errors")
        void unknownScenario() {
            var cause = new RuntimeException("Unexpected error");
            var exception = new LlmException(
                LlmException.ErrorCode.UNKNOWN,
                "An unexpected error occurred",
                cause
            );
            assertThat(exception.getCode()).isEqualTo(LlmException.ErrorCode.UNKNOWN);
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("Exception inheritance")
    class ExceptionInheritanceTests {

        @Test
        @DisplayName("should extend Exception (checked exception)")
        void shouldExtendException() {
            assertThat(Exception.class).isAssignableFrom(LlmException.class);
        }
    }
}
