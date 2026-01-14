package ee.jwright.java.write;

import ee.jwright.core.write.WriteMode;
import ee.jwright.core.write.WriteRequest;
import ee.jwright.core.write.WriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaMethodBodyWriter}.
 */
class JavaMethodBodyWriterTest {

    @TempDir
    Path tempDir;

    private JavaMethodBodyWriter writer;

    @BeforeEach
    void setUp() {
        writer = new JavaMethodBodyWriter();
    }

    @Test
    @DisplayName("should have id 'java-method-body'")
    void shouldHaveCorrectId() {
        assertThat(writer.getId()).isEqualTo("java-method-body");
    }

    @Test
    @DisplayName("should have order 100")
    void shouldHaveCorrectOrder() {
        assertThat(writer.getOrder()).isEqualTo(100);
    }

    @Test
    @DisplayName("should support .java files")
    void shouldSupportJavaFiles() throws IOException {
        Path javaFile = tempDir.resolve("Calculator.java");
        Files.writeString(javaFile, "class Calculator {}");
        WriteRequest request = new WriteRequest(javaFile, "add", "return a + b;", WriteMode.INJECT);

        assertThat(writer.supports(request)).isTrue();
    }

    @Test
    @DisplayName("should not support non-Java files")
    void shouldNotSupportNonJavaFiles() throws IOException {
        Path ktFile = tempDir.resolve("Calculator.kt");
        Files.writeString(ktFile, "class Calculator {}");
        WriteRequest request = new WriteRequest(ktFile, "add", "return a + b", WriteMode.INJECT);

        assertThat(writer.supports(request)).isFalse();
    }

    @Nested
    @DisplayName("INJECT mode")
    class InjectModeTests {

        @Test
        @DisplayName("should inject body into empty method")
        void shouldInjectBodyIntoEmptyMethod() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                    }
                }
                """);
            WriteRequest request = new WriteRequest(javaFile, "add", "return a + b;", WriteMode.INJECT);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("return a + b;");
        }

        @Test
        @DisplayName("should inject body into method with placeholder")
        void shouldInjectBodyIntoMethodWithPlaceholder() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                        throw new UnsupportedOperationException("Not implemented");
                    }
                }
                """);
            WriteRequest request = new WriteRequest(javaFile, "add", "return a + b;", WriteMode.INJECT);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("return a + b;");
            assertThat(content).doesNotContain("UnsupportedOperationException");
        }

        @Test
        @DisplayName("should inject multi-line body")
        void shouldInjectMultiLineBody() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Service {
                    public String process(String input) {
                    }
                }
                """);
            String body = """
                if (input == null) {
                    return "";
                }
                return input.toUpperCase();""";
            WriteRequest request = new WriteRequest(javaFile, "process", body, WriteMode.INJECT);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("if (input == null)");
            assertThat(content).contains("return input.toUpperCase()");
        }

        @Test
        @DisplayName("should fail if method not found")
        void shouldFailIfMethodNotFound() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                    }
                }
                """);
            WriteRequest request = new WriteRequest(javaFile, "subtract", "return a - b;", WriteMode.INJECT);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Method 'subtract' not found");
        }
    }

    @Nested
    @DisplayName("REPLACE mode")
    class ReplaceModeTests {

        @Test
        @DisplayName("should replace entire method body")
        void shouldReplaceEntireMethodBody() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                        // Old implementation
                        int result = a + b;
                        return result;
                    }
                }
                """);
            WriteRequest request = new WriteRequest(javaFile, "add", "return a + b;", WriteMode.REPLACE);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("return a + b;");
            assertThat(content).doesNotContain("Old implementation");
            assertThat(content).doesNotContain("int result = a + b");
        }

        @Test
        @DisplayName("should replace body preserving method signature")
        void shouldReplaceBodyPreservingSignature() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                        return 0;
                    }
                }
                """);
            WriteRequest request = new WriteRequest(javaFile, "add", "return a + b;", WriteMode.REPLACE);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("public int add(int a, int b)");
            assertThat(content).contains("return a + b;");
        }

        @Test
        @DisplayName("should replace with multi-statement body")
        void shouldReplaceWithMultiStatementBody() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Service {
                    public String transform(String input) {
                        return input;
                    }
                }
                """);
            String body = """
                String result = input.trim();
                result = result.toUpperCase();
                return result;""";
            WriteRequest request = new WriteRequest(javaFile, "transform", body, WriteMode.REPLACE);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("String result = input.trim()");
            assertThat(content).contains("result = result.toUpperCase()");
            assertThat(content).contains("return result");
        }

        @Test
        @DisplayName("should preserve other methods")
        void shouldPreserveOtherMethods() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                        return 0;
                    }

                    public int subtract(int a, int b) {
                        return a - b;
                    }
                }
                """);
            WriteRequest request = new WriteRequest(javaFile, "add", "return a + b;", WriteMode.REPLACE);

            // When
            WriteResult result = writer.write(request);

            // Then
            assertThat(result.success()).isTrue();
            String content = Files.readString(javaFile);
            assertThat(content).contains("return a + b;");
            assertThat(content).contains("public int subtract(int a, int b)");
            assertThat(content).contains("return a - b;");
        }
    }
}
