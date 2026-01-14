package ee.jwright.java.extract;

import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaTargetMethodExtractor}.
 */
class JavaTargetMethodExtractorTest {

    @TempDir
    Path tempDir;

    private JavaTargetMethodExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaTargetMethodExtractor();
    }

    @Test
    @DisplayName("should support existing .java impl files")
    void supports_withJavaImplFile_returnsTrue() throws IOException {
        // Given
        Path implFile = tempDir.resolve("Calculator.java");
        Files.writeString(implFile, "class Calculator {}");
        ExtractionRequest request = new ExtractionRequest(
            tempDir.resolve("CalculatorTest.java"),
            "CalculatorTest",
            "testAdd",
            implFile,
            "add",
            tempDir
        );

        // When/Then
        assertThat(extractor.supports(request)).isTrue();
    }

    @Test
    @DisplayName("should not support non-.java files")
    void supports_withNonJavaFile_returnsFalse() throws IOException {
        // Given
        Path implFile = tempDir.resolve("Calculator.kt");
        Files.writeString(implFile, "class Calculator {}");
        ExtractionRequest request = new ExtractionRequest(
            tempDir.resolve("CalculatorTest.java"),
            "CalculatorTest",
            "testAdd",
            implFile,
            "add",
            tempDir
        );

        // When/Then
        assertThat(extractor.supports(request)).isFalse();
    }

    @Test
    @DisplayName("should not support non-existent files")
    void supports_withNonExistentFile_returnsFalse() {
        // Given
        Path implFile = tempDir.resolve("NonExistent.java");
        ExtractionRequest request = new ExtractionRequest(
            tempDir.resolve("CalculatorTest.java"),
            "CalculatorTest",
            "testAdd",
            implFile,
            "add",
            tempDir
        );

        // When/Then
        assertThat(extractor.supports(request)).isFalse();
    }

    @Test
    @DisplayName("should extract target method signature when method exists")
    void extract_findsTargetMethod_setsSignature() throws IOException {
        // Given
        Path implFile = tempDir.resolve("Calculator.java");
        Files.writeString(implFile, """
            public class Calculator {
                public int add(int a, int b) {
                    // TODO: implement
                    return 0;
                }
            }
            """);
        ExtractionRequest request = new ExtractionRequest(
            tempDir.resolve("CalculatorTest.java"),
            "CalculatorTest",
            "testAdd",
            implFile,
            "add",
            tempDir
        );
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.targetSignature()).isNotNull();
        assertThat(context.targetSignature().name()).isEqualTo("add");
        assertThat(context.targetSignature().returnType()).isEqualTo("int");
        assertThat(context.targetSignature().parameters()).containsExactly("int a", "int b");
    }

    @Test
    @DisplayName("should not set signature when method not found")
    void extract_methodNotFound_doesNotSetSignature() throws IOException {
        // Given
        Path implFile = tempDir.resolve("Calculator.java");
        Files.writeString(implFile, """
            public class Calculator {
                public void doSomething() {
                    System.out.println("hello");
                }
            }
            """);
        ExtractionRequest request = new ExtractionRequest(
            tempDir.resolve("CalculatorTest.java"),
            "CalculatorTest",
            "testAdd",
            implFile,
            "add",
            tempDir
        );
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.targetSignature()).isNull();
    }

    @Test
    @DisplayName("should extract current implementation when method has body")
    void extract_withMethodBody_setsCurrentImplementation() throws IOException {
        // Given
        Path implFile = tempDir.resolve("Calculator.java");
        Files.writeString(implFile, """
            public class Calculator {
                public int add(int a, int b) {
                    // TODO: implement
                    return 0;
                }
            }
            """);
        ExtractionRequest request = new ExtractionRequest(
            tempDir.resolve("CalculatorTest.java"),
            "CalculatorTest",
            "testAdd",
            implFile,
            "add",
            tempDir
        );
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.currentImplementation()).isNotNull();
        assertThat(context.currentImplementation())
            .contains("// TODO: implement")
            .contains("return 0;");
    }
}
