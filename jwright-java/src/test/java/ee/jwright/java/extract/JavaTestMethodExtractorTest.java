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
 * Tests for {@link JavaTestMethodExtractor}.
 */
class JavaTestMethodExtractorTest {

    @TempDir
    Path tempDir;

    private JavaTestMethodExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaTestMethodExtractor();
    }

    @Test
    @DisplayName("should have id 'java-test-method'")
    void shouldHaveCorrectId() {
        assertThat(extractor.getId()).isEqualTo("java-test-method");
    }

    @Test
    @DisplayName("should have order 100 (test structure range)")
    void shouldHaveCorrectOrder() {
        assertThat(extractor.getOrder()).isEqualTo(100);
    }

    @Test
    @DisplayName("should support .java files")
    void shouldSupportJavaFiles() throws IOException {
        // Given
        Path testFile = tempDir.resolve("CalculatorTest.java");
        Files.writeString(testFile, "class Test {}");
        ExtractionRequest request = createRequest(testFile);

        // When/Then
        assertThat(extractor.supports(request)).isTrue();
    }

    @Test
    @DisplayName("should not support non-Java files")
    void shouldNotSupportNonJavaFiles() throws IOException {
        // Given
        Path testFile = tempDir.resolve("CalculatorTest.kt");
        Files.writeString(testFile, "class Test {}");
        ExtractionRequest request = createRequest(testFile);

        // When/Then
        assertThat(extractor.supports(request)).isFalse();
    }

    @Test
    @DisplayName("should extract test class name")
    void shouldExtractTestClassName() throws IOException {
        // Given
        Path testFile = createTestFile("CalculatorTest", """
            package com.example;

            import org.junit.jupiter.api.Test;

            class CalculatorTest {
                @Test
                void shouldAddTwoNumbers() {
                    Calculator calc = new Calculator();
                    int result = calc.add(2, 3);
                    assertEquals(5, result);
                }
            }
            """);
        ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAddTwoNumbers");
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.testClassName()).isEqualTo("CalculatorTest");
    }

    @Test
    @DisplayName("should extract test method name")
    void shouldExtractTestMethodName() throws IOException {
        // Given
        Path testFile = createTestFile("CalculatorTest", """
            package com.example;

            import org.junit.jupiter.api.Test;

            class CalculatorTest {
                @Test
                void shouldAddTwoNumbers() {
                    Calculator calc = new Calculator();
                    int result = calc.add(2, 3);
                    assertEquals(5, result);
                }
            }
            """);
        ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAddTwoNumbers");
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.testMethodName()).isEqualTo("shouldAddTwoNumbers");
    }

    @Test
    @DisplayName("should extract test method body")
    void shouldExtractTestMethodBody() throws IOException {
        // Given
        Path testFile = createTestFile("CalculatorTest", """
            package com.example;

            import org.junit.jupiter.api.Test;

            class CalculatorTest {
                @Test
                void shouldAddTwoNumbers() {
                    Calculator calc = new Calculator();
                    int result = calc.add(2, 3);
                    assertEquals(5, result);
                }
            }
            """);
        ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAddTwoNumbers");
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.testMethodBody())
            .contains("Calculator calc = new Calculator()")
            .contains("int result = calc.add(2, 3)")
            .contains("assertEquals(5, result)");
    }

    @Test
    @DisplayName("should handle test method with multiple statements")
    void shouldHandleMultipleStatements() throws IOException {
        // Given
        Path testFile = createTestFile("ServiceTest", """
            package com.example;

            import org.junit.jupiter.api.Test;

            class ServiceTest {
                @Test
                void shouldProcessData() {
                    // Given
                    Service service = new Service();
                    String input = "hello";

                    // When
                    String result = service.process(input);

                    // Then
                    assertNotNull(result);
                    assertEquals("HELLO", result);
                }
            }
            """);
        ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldProcessData");
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.testMethodBody())
            .contains("Service service = new Service()")
            .contains("String input = \"hello\"")
            .contains("String result = service.process(input)")
            .contains("assertNotNull(result)")
            .contains("assertEquals(\"HELLO\", result)");
    }

    @Test
    @DisplayName("should handle missing method gracefully")
    void shouldHandleMissingMethodGracefully() throws IOException {
        // Given
        Path testFile = createTestFile("EmptyTest", """
            package com.example;

            class EmptyTest {
            }
            """);
        ExtractionRequest request = createRequest(testFile, "EmptyTest", "nonExistentMethod");
        ExtractionContext.Builder builder = ExtractionContext.builder();

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.testClassName()).isEqualTo("EmptyTest");
        assertThat(context.testMethodName()).isNull();
        assertThat(context.testMethodBody()).isNull();
    }

    private Path createTestFile(String className, String content) throws IOException {
        Path testFile = tempDir.resolve(className + ".java");
        Files.writeString(testFile, content);
        return testFile;
    }

    private ExtractionRequest createRequest(Path testFile) {
        return new ExtractionRequest(testFile, "Test", "test", null, null, tempDir);
    }

    private ExtractionRequest createRequest(Path testFile, String className, String methodName) {
        return new ExtractionRequest(testFile, className, methodName, null, null, tempDir);
    }
}
