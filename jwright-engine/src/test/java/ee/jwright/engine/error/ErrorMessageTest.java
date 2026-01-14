package ee.jwright.engine.error;

import ee.jwright.core.exception.JwrightException;
import ee.jwright.core.exception.JwrightException.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for error message quality.
 * <p>
 * Verifies that error messages are:
 * - Clear and understandable
 * - Actionable (suggest what to do)
 * - Include relevant context
 * </p>
 *
 * <h2>Task 9.6: Error messages</h2>
 */
@DisplayName("9.6 Error messages")
class ErrorMessageTest {

    @Nested
    @DisplayName("ErrorMessages utility")
    class ErrorMessagesUtilityTests {

        @Test
        @DisplayName("NO_BUILD_TOOL message is clear and actionable")
        void noBuildToolMessageIsClearAndActionable() {
            String message = ErrorMessages.noBuildToolFound("/path/to/project");

            assertThat(message)
                .contains("No build tool found")
                .containsAnyOf("pom.xml", "build.gradle", "Maven", "Gradle")
                .contains("/path/to/project");
        }

        @Test
        @DisplayName("NO_TEST_FOUND message includes test target")
        void noTestFoundMessageIncludesTestTarget() {
            String message = ErrorMessages.testNotFound("CalculatorTest", "testAdd");

            assertThat(message)
                .contains("CalculatorTest")
                .contains("testAdd")
                .containsAnyOf("not found", "does not exist");
        }

        @Test
        @DisplayName("NO_IMPL_FOUND message suggests creating the file")
        void noImplFoundMessageSuggestsCreatingFile() {
            String message = ErrorMessages.implementationNotFound(
                "CalculatorTest",
                "Calculator"
            );

            assertThat(message)
                .contains("Calculator")
                .containsAnyOf("create", "implement", "not found");
        }

        @Test
        @DisplayName("EXTRACTION_FAILED message explains what went wrong")
        void extractionFailedMessageExplainsWhatWentWrong() {
            String message = ErrorMessages.extractionFailed(
                "CalculatorTest#testAdd",
                "Syntax error at line 5"
            );

            assertThat(message)
                .contains("CalculatorTest#testAdd")
                .contains("Syntax error at line 5");
        }

        @Test
        @DisplayName("GENERATION_FAILED message includes retry info")
        void generationFailedMessageIncludesRetryInfo() {
            String message = ErrorMessages.generationFailed(
                "CalculatorTest#testAdd",
                5,
                "Model not responding"
            );

            assertThat(message)
                .contains("CalculatorTest#testAdd")
                .contains("5")
                .containsAnyOf("attempt", "retrie");
        }

        @Test
        @DisplayName("VALIDATION_FAILED message distinguishes compile vs test errors")
        void validationFailedMessageDistinguishesCompileVsTestErrors() {
            String compileError = ErrorMessages.compilationFailed(
                "Calculator.java",
                3,
                "';' expected"
            );

            String testError = ErrorMessages.testFailed(
                "CalculatorTest",
                "testAdd",
                "expected: <5> but was: <0>"
            );

            assertThat(compileError)
                .contains("Calculator.java")
                .contains("line 3")
                .contains("';' expected");

            assertThat(testError)
                .contains("CalculatorTest")
                .contains("testAdd")
                .contains("expected");
        }

        @Test
        @DisplayName("CONFIG_INVALID message identifies the problem")
        void configInvalidMessageIdentifiesTheProblem() {
            String message = ErrorMessages.configInvalid(
                "jwright.llm.model",
                "Model name cannot be empty"
            );

            assertThat(message)
                .contains("jwright.llm.model")
                .containsAnyOf("empty", "invalid");
        }

        @Test
        @DisplayName("LLM connection error provides troubleshooting steps")
        void llmConnectionErrorProvidesTroubleshootingSteps() {
            String message = ErrorMessages.llmConnectionFailed("http://localhost:11434");

            assertThat(message)
                .contains("http://localhost:11434")
                .containsAnyOf("running", "Ollama", "connect");
        }
    }

    @Nested
    @DisplayName("Exception creation")
    class ExceptionCreationTests {

        @Test
        @DisplayName("can create exception with clear message")
        void canCreateExceptionWithClearMessage() {
            JwrightException ex = new JwrightException(
                ErrorCode.NO_BUILD_TOOL,
                ErrorMessages.noBuildToolFound("/project")
            );

            assertThat(ex.getCode()).isEqualTo(ErrorCode.NO_BUILD_TOOL);
            assertThat(ex.getMessage()).contains("No build tool");
        }

        @Test
        @DisplayName("exception message chain preserved")
        void exceptionMessageChainPreserved() {
            RuntimeException cause = new RuntimeException("Connection refused");
            JwrightException ex = new JwrightException(
                ErrorCode.GENERATION_FAILED,
                ErrorMessages.llmConnectionFailed("http://localhost:11434"),
                cause
            );

            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getMessage()).contains("http://localhost:11434");
        }
    }

    @Nested
    @DisplayName("Message formatting")
    class MessageFormattingTests {

        @Test
        @DisplayName("messages do not contain stack traces")
        void messagesDoNotContainStackTraces() {
            String message = ErrorMessages.generationFailed(
                "Test#method",
                3,
                "Some error"
            );

            assertThat(message)
                .doesNotContain("at ")
                .doesNotContain(".java:");
        }

        @Test
        @DisplayName("messages are reasonably short")
        void messagesAreReasonablyShort() {
            String message = ErrorMessages.noBuildToolFound("/path/to/some/project");

            // Messages should be readable in a terminal
            assertThat(message.length()).isLessThan(300);
        }

        @Test
        @DisplayName("messages can be displayed on multiple lines")
        void messagesCanBeDisplayedOnMultipleLines() {
            String message = ErrorMessages.compilationFailed(
                "Calculator.java",
                5,
                "cannot find symbol\n  symbol: variable foo\n  location: class Calculator"
            );

            // Multi-line compiler errors should be preserved
            assertThat(message).contains("\n");
        }
    }
}
