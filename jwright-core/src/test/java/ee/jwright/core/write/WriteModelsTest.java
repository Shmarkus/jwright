package ee.jwright.core.write;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for write models (WriteMode, WriteRequest, WriteResult).
 */
@DisplayName("Write Models")
class WriteModelsTest {

    @Nested
    @DisplayName("WriteMode enum")
    class WriteModeTests {

        @Test
        @DisplayName("should have all required write modes")
        void shouldHaveAllRequiredWriteModes() {
            assertThat(WriteMode.values())
                .containsExactlyInAnyOrder(
                    WriteMode.INJECT,
                    WriteMode.REPLACE,
                    WriteMode.APPEND,
                    WriteMode.CREATE
                );
        }

        @Test
        @DisplayName("INJECT - insert method body into existing method")
        void injectMode() {
            assertThat(WriteMode.INJECT.name()).isEqualTo("INJECT");
        }

        @Test
        @DisplayName("REPLACE - replace entire method")
        void replaceMode() {
            assertThat(WriteMode.REPLACE.name()).isEqualTo("REPLACE");
        }

        @Test
        @DisplayName("APPEND - add new method to class")
        void appendMode() {
            assertThat(WriteMode.APPEND.name()).isEqualTo("APPEND");
        }

        @Test
        @DisplayName("CREATE - create new file")
        void createMode() {
            assertThat(WriteMode.CREATE.name()).isEqualTo("CREATE");
        }
    }

    @Nested
    @DisplayName("WriteRequest record")
    class WriteRequestTests {

        @Test
        @DisplayName("should create request with all fields")
        void shouldCreateRequestWithAllFields() {
            var targetFile = Path.of("/project/src/main/java/Foo.java");
            var generatedCode = "return a + b;";

            var request = new WriteRequest(targetFile, "add", generatedCode, WriteMode.INJECT);

            assertThat(request.targetFile()).isEqualTo(targetFile);
            assertThat(request.targetMethodName()).isEqualTo("add");
            assertThat(request.generatedCode()).isEqualTo(generatedCode);
            assertThat(request.mode()).isEqualTo(WriteMode.INJECT);
        }

        @Test
        @DisplayName("should create request for creating new file")
        void shouldCreateRequestForNewFile() {
            var targetFile = Path.of("/project/src/main/java/NewClass.java");
            var fullClassCode = "public class NewClass { }";

            var request = new WriteRequest(targetFile, null, fullClassCode, WriteMode.CREATE);

            assertThat(request.targetMethodName()).isNull();
            assertThat(request.mode()).isEqualTo(WriteMode.CREATE);
        }

        @Test
        @DisplayName("should implement record equality")
        void shouldImplementRecordEquality() {
            var path = Path.of("/foo/bar.java");

            var request1 = new WriteRequest(path, "method", "code", WriteMode.INJECT);
            var request2 = new WriteRequest(path, "method", "code", WriteMode.INJECT);

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }
    }

    @Nested
    @DisplayName("WriteResult record")
    class WriteResultTests {

        @Test
        @DisplayName("success factory should create successful result")
        void successFactoryShouldCreateSuccessfulResult() {
            var result = WriteResult.ok();

            assertThat(result.success()).isTrue();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("failure factory should create failed result with message")
        void failureFactoryShouldCreateFailedResult() {
            var result = WriteResult.failure("Method not found: add");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("Method not found: add");
        }

        @Test
        @DisplayName("should implement record equality")
        void shouldImplementRecordEquality() {
            var result1 = WriteResult.ok();
            var result2 = WriteResult.ok();

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("different results should not be equal")
        void differentResultsShouldNotBeEqual() {
            var success = WriteResult.ok();
            var failure = WriteResult.failure("error");

            assertThat(success).isNotEqualTo(failure);
        }
    }
}
