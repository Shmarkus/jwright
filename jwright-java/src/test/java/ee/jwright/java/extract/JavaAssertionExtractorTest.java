package ee.jwright.java.extract;

import ee.jwright.core.extract.Assertion;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaAssertionExtractor}.
 */
class JavaAssertionExtractorTest {

    @TempDir
    Path tempDir;

    private JavaAssertionExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaAssertionExtractor();
    }

    @Test
    @DisplayName("should have id 'java-assertion'")
    void shouldHaveCorrectId() {
        assertThat(extractor.getId()).isEqualTo("java-assertion");
    }

    @Test
    @DisplayName("should have order 200 (assertions range)")
    void shouldHaveCorrectOrder() {
        assertThat(extractor.getOrder()).isEqualTo(200);
    }

    @Test
    @DisplayName("should support .java files")
    void shouldSupportJavaFiles() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "class Test {}");
        ExtractionRequest request = createRequest(testFile);

        assertThat(extractor.supports(request)).isTrue();
    }

    @Nested
    @DisplayName("JUnit assertEquals extraction")
    class AssertEqualsTests {

        @Test
        @DisplayName("should extract assertEquals with two arguments")
        void shouldExtractSimpleAssertEquals() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.junit.jupiter.api.Assertions.*;

                class CalculatorTest {
                    @Test
                    void shouldAdd() {
                        Calculator calc = new Calculator();
                        int result = calc.add(2, 3);
                        assertEquals(5, result);
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAdd");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertEquals");
            assertThat(assertion.expected()).isEqualTo("5");
            assertThat(assertion.actual()).isEqualTo("result");
        }

        @Test
        @DisplayName("should extract assertEquals with message")
        void shouldExtractAssertEqualsWithMessage() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.junit.jupiter.api.Assertions.*;

                class CalculatorTest {
                    @Test
                    void shouldAdd() {
                        assertEquals(5, result, "Sum should be 5");
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAdd");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertEquals");
            assertThat(assertion.message()).isEqualTo("\"Sum should be 5\"");
        }

        @Test
        @DisplayName("should extract multiple assertEquals calls")
        void shouldExtractMultipleAssertEquals() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.junit.jupiter.api.Assertions.*;

                class CalculatorTest {
                    @Test
                    void shouldCalculate() {
                        assertEquals(5, add(2, 3));
                        assertEquals(10, multiply(2, 5));
                        assertEquals(3, subtract(5, 2));
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldCalculate");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(3);
        }

        @Test
        @DisplayName("should extract assertNotNull")
        void shouldExtractAssertNotNull() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.junit.jupiter.api.Assertions.*;

                class ServiceTest {
                    @Test
                    void shouldReturnResult() {
                        assertNotNull(service.process());
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldReturnResult");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertNotNull");
            assertThat(assertion.actual()).isEqualTo("service.process()");
        }

        @Test
        @DisplayName("should extract assertTrue")
        void shouldExtractAssertTrue() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.junit.jupiter.api.Assertions.*;

                class ValidatorTest {
                    @Test
                    void shouldValidate() {
                        assertTrue(validator.isValid(input));
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ValidatorTest", "shouldValidate");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertTrue");
            assertThat(assertion.actual()).isEqualTo("validator.isValid(input)");
        }

        @Test
        @DisplayName("should extract assertFalse")
        void shouldExtractAssertFalse() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.junit.jupiter.api.Assertions.*;

                class ValidatorTest {
                    @Test
                    void shouldRejectInvalid() {
                        assertFalse(validator.isValid(invalidInput));
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ValidatorTest", "shouldRejectInvalid");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertFalse");
        }
    }

    @Nested
    @DisplayName("AssertJ assertThat extraction")
    class AssertThatTests {

        @Test
        @DisplayName("should extract assertThat().isEqualTo()")
        void shouldExtractAssertThatIsEqualTo() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.assertj.core.api.Assertions.assertThat;

                class CalculatorTest {
                    @Test
                    void shouldAdd() {
                        int result = calc.add(2, 3);
                        assertThat(result).isEqualTo(5);
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "CalculatorTest", "shouldAdd");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertThat");
            assertThat(assertion.actual()).isEqualTo("result");
            assertThat(assertion.expected()).isEqualTo("isEqualTo(5)");
        }

        @Test
        @DisplayName("should extract assertThat().isNotNull()")
        void shouldExtractAssertThatIsNotNull() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.assertj.core.api.Assertions.assertThat;

                class ServiceTest {
                    @Test
                    void shouldReturnValue() {
                        assertThat(service.getValue()).isNotNull();
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ServiceTest", "shouldReturnValue");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertThat");
            assertThat(assertion.actual()).isEqualTo("service.getValue()");
            assertThat(assertion.expected()).isEqualTo("isNotNull()");
        }

        @Test
        @DisplayName("should extract assertThat().isTrue()")
        void shouldExtractAssertThatIsTrue() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.assertj.core.api.Assertions.assertThat;

                class ValidatorTest {
                    @Test
                    void shouldValidate() {
                        assertThat(validator.isValid(input)).isTrue();
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ValidatorTest", "shouldValidate");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertThat");
            assertThat(assertion.expected()).isEqualTo("isTrue()");
        }

        @Test
        @DisplayName("should extract assertThat with chained methods")
        void shouldExtractAssertThatWithChainedMethods() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.assertj.core.api.Assertions.assertThat;

                class ListTest {
                    @Test
                    void shouldContainItems() {
                        assertThat(list).hasSize(3).contains("a", "b");
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "ListTest", "shouldContainItems");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(1);
            Assertion assertion = context.assertions().get(0);
            assertThat(assertion.type()).isEqualTo("assertThat");
            assertThat(assertion.actual()).isEqualTo("list");
            // Chained expectations are captured
            assertThat(assertion.expected()).contains("hasSize(3)");
        }

        @Test
        @DisplayName("should extract multiple assertThat calls")
        void shouldExtractMultipleAssertThat() throws IOException {
            // Given
            Path testFile = createTestFile("""
                package com.example;

                import static org.assertj.core.api.Assertions.assertThat;

                class PersonTest {
                    @Test
                    void shouldCreatePerson() {
                        Person p = new Person("John", 30);
                        assertThat(p.getName()).isEqualTo("John");
                        assertThat(p.getAge()).isEqualTo(30);
                    }
                }
                """);
            ExtractionContext.Builder builder = ExtractionContext.builder();
            ExtractionRequest request = createRequest(testFile, "PersonTest", "shouldCreatePerson");

            // When
            extractor.extract(request, builder);
            ExtractionContext context = builder.build();

            // Then
            assertThat(context.assertions()).hasSize(2);
        }
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
