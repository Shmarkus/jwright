package ee.jwright.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwrightException.
 * Validates exception creation, error codes, and inheritance.
 */
@DisplayName("JwrightException")
class JwrightExceptionTest {

    @Nested
    @DisplayName("ErrorCode enum")
    class ErrorCodeTests {

        @Test
        @DisplayName("should have all required error codes")
        void shouldHaveAllRequiredErrorCodes() {
            assertThat(JwrightException.ErrorCode.values())
                .containsExactlyInAnyOrder(
                    JwrightException.ErrorCode.NO_BUILD_TOOL,
                    JwrightException.ErrorCode.NO_TEST_FOUND,
                    JwrightException.ErrorCode.NO_IMPL_FOUND,
                    JwrightException.ErrorCode.EXTRACTION_FAILED,
                    JwrightException.ErrorCode.GENERATION_FAILED,
                    JwrightException.ErrorCode.VALIDATION_FAILED,
                    JwrightException.ErrorCode.CONFIG_INVALID
                );
        }

        @ParameterizedTest
        @EnumSource(JwrightException.ErrorCode.class)
        @DisplayName("should be able to retrieve each error code by name")
        void shouldRetrieveErrorCodeByName(JwrightException.ErrorCode code) {
            assertThat(JwrightException.ErrorCode.valueOf(code.name())).isEqualTo(code);
        }
    }

    @Nested
    @DisplayName("Exception creation")
    class ExceptionCreationTests {

        @Test
        @DisplayName("should create exception with code and message")
        void shouldCreateExceptionWithCodeAndMessage() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.NO_BUILD_TOOL,
                "No Maven or Gradle found"
            );

            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.NO_BUILD_TOOL);
            assertThat(exception.getMessage()).isEqualTo("No Maven or Gradle found");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("should create exception with code, message, and cause")
        void shouldCreateExceptionWithCodeMessageAndCause() {
            var cause = new RuntimeException("Underlying error");
            var exception = new JwrightException(
                JwrightException.ErrorCode.EXTRACTION_FAILED,
                "Failed to parse test file",
                cause
            );

            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.EXTRACTION_FAILED);
            assertThat(exception.getMessage()).isEqualTo("Failed to parse test file");
            assertThat(exception.getCause()).isEqualTo(cause);
        }

        @ParameterizedTest
        @EnumSource(JwrightException.ErrorCode.class)
        @DisplayName("should be throwable with any error code")
        void shouldBeThrowableWithAnyErrorCode(JwrightException.ErrorCode code) {
            assertThatThrownBy(() -> {
                throw new JwrightException(code, "Test message for " + code);
            })
                .isInstanceOf(JwrightException.class)
                .hasMessage("Test message for " + code)
                .satisfies(e -> assertThat(((JwrightException) e).getCode()).isEqualTo(code));
        }
    }

    @Nested
    @DisplayName("Exception inheritance")
    class ExceptionInheritanceTests {

        @Test
        @DisplayName("should extend Exception (checked exception)")
        void shouldExtendException() {
            assertThat(Exception.class).isAssignableFrom(JwrightException.class);
        }

        @Test
        @DisplayName("should not extend RuntimeException")
        void shouldNotExtendRuntimeException() {
            assertThat(RuntimeException.class.isAssignableFrom(JwrightException.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("Error scenarios")
    class ErrorScenarioTests {

        @Test
        @DisplayName("NO_BUILD_TOOL - when project has no Maven or Gradle")
        void noBuildToolScenario() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.NO_BUILD_TOOL,
                "No pom.xml or build.gradle found in /project"
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.NO_BUILD_TOOL);
        }

        @Test
        @DisplayName("NO_TEST_FOUND - when specified test does not exist")
        void noTestFoundScenario() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.NO_TEST_FOUND,
                "Test class FooTest not found"
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.NO_TEST_FOUND);
        }

        @Test
        @DisplayName("NO_IMPL_FOUND - when implementation file is missing")
        void noImplFoundScenario() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.NO_IMPL_FOUND,
                "Implementation class Foo not found for FooTest"
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.NO_IMPL_FOUND);
        }

        @Test
        @DisplayName("EXTRACTION_FAILED - when test parsing fails")
        void extractionFailedScenario() {
            var parseError = new IllegalStateException("Invalid syntax");
            var exception = new JwrightException(
                JwrightException.ErrorCode.EXTRACTION_FAILED,
                "Failed to extract context from FooTest#testBar",
                parseError
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.EXTRACTION_FAILED);
            assertThat(exception.getCause()).isEqualTo(parseError);
        }

        @Test
        @DisplayName("GENERATION_FAILED - when LLM fails to generate valid code")
        void generationFailedScenario() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.GENERATION_FAILED,
                "Failed to generate implementation after 5 attempts"
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.GENERATION_FAILED);
        }

        @Test
        @DisplayName("VALIDATION_FAILED - when generated code fails tests")
        void validationFailedScenario() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.VALIDATION_FAILED,
                "Generated code compiles but test testBar fails"
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("CONFIG_INVALID - when configuration is malformed")
        void configInvalidScenario() {
            var exception = new JwrightException(
                JwrightException.ErrorCode.CONFIG_INVALID,
                "Invalid YAML in .jwright/config.yaml: missing 'llm.provider'"
            );
            assertThat(exception.getCode()).isEqualTo(JwrightException.ErrorCode.CONFIG_INVALID);
        }
    }
}
