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
 * Tests for {@link JavaHintExtractor}.
 */
class JavaHintExtractorTest {

    @TempDir
    Path tempDir;

    private JavaHintExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaHintExtractor();
    }

    @Test
    @DisplayName("should have id 'java-hint'")
    void shouldHaveCorrectId() {
        assertThat(extractor.getId()).isEqualTo("java-hint");
    }

    @Test
    @DisplayName("should have order 400 (hints range)")
    void shouldHaveCorrectOrder() {
        assertThat(extractor.getOrder()).isEqualTo(400);
    }

    @Test
    @DisplayName("should support .java files")
    void shouldSupportJavaFiles() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "class Test {}");
        ExtractionRequest request = createRequest(testFile);

        assertThat(extractor.supports(request)).isTrue();
    }

    @Test
    @DisplayName("should extract single @JwrightHint annotation")
    void shouldExtractSingleHint() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import ee.jwright.core.annotation.JwrightHint;
            import org.junit.jupiter.api.Test;

            class CalculatorTest {
                @Test
                @JwrightHint("Use addition operator")
                void shouldAddNumbers() {
                    Calculator calc = new Calculator();
                    assertEquals(5, calc.add(2, 3));
                }
            }
            """);
        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAddNumbers");

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.hints()).hasSize(1);
        assertThat(context.hints().get(0)).isEqualTo("Use addition operator");
    }

    @Test
    @DisplayName("should extract multiple @JwrightHint annotations")
    void shouldExtractMultipleHints() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import ee.jwright.core.annotation.JwrightHint;
            import org.junit.jupiter.api.Test;

            class FactorialTest {
                @Test
                @JwrightHint("Use recursion")
                @JwrightHint("Base case is n <= 1")
                void shouldCalculateFactorial() {
                    assertEquals(120, factorial(5));
                }
            }
            """);
        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = createRequest(testFile, "FactorialTest", "shouldCalculateFactorial");

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.hints()).hasSize(2);
        assertThat(context.hints()).containsExactly("Use recursion", "Base case is n <= 1");
    }

    @Test
    @DisplayName("should handle method without hints")
    void shouldHandleMethodWithoutHints() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class SimpleTest {
                @Test
                void shouldDoSomething() {
                    // No hints here
                }
            }
            """);
        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = createRequest(testFile, "SimpleTest", "shouldDoSomething");

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.hints()).isEmpty();
    }

    @Test
    @DisplayName("should extract hint with special characters")
    void shouldExtractHintWithSpecialCharacters() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import ee.jwright.core.annotation.JwrightHint;
            import org.junit.jupiter.api.Test;

            class StringTest {
                @Test
                @JwrightHint("Handle null and empty strings; use trim()")
                void shouldTrimString() {
                    // test
                }
            }
            """);
        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = createRequest(testFile, "StringTest", "shouldTrimString");

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.hints()).hasSize(1);
        assertThat(context.hints().get(0)).isEqualTo("Handle null and empty strings; use trim()");
    }

    @Test
    @DisplayName("should only extract hints for the specified method")
    void shouldOnlyExtractHintsForSpecifiedMethod() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import ee.jwright.core.annotation.JwrightHint;
            import org.junit.jupiter.api.Test;

            class MultiTest {
                @Test
                @JwrightHint("Hint for method A")
                void methodA() {
                }

                @Test
                @JwrightHint("Hint for method B")
                void methodB() {
                }
            }
            """);
        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = createRequest(testFile, "MultiTest", "methodA");

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.hints()).hasSize(1);
        assertThat(context.hints().get(0)).isEqualTo("Hint for method A");
    }

    private Path createTestFile(String content) throws IOException {
        Path testFile = tempDir.resolve("Test.java");
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
