package ee.jwright.java;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JavaParserUtils}.
 */
class JavaParserUtilsTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("parse(Path)")
    class ParseTests {

        @Test
        @DisplayName("should parse valid Java file and return CompilationUnit")
        void shouldParseValidJavaFile() throws IOException {
            // Given
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """);

            // When
            CompilationUnit cu = JavaParserUtils.parse(javaFile);

            // Then
            assertThat(cu).isNotNull();
            assertThat(cu.getPackageDeclaration()).isPresent();
            assertThat(cu.getPackageDeclaration().get().getNameAsString()).isEqualTo("com.example");
            assertThat(cu.getClassByName("Calculator")).isPresent();
        }

        @Test
        @DisplayName("should throw IOException for non-existent file")
        void shouldThrowForNonExistentFile() {
            // Given
            Path nonExistent = tempDir.resolve("NonExistent.java");

            // When/Then
            assertThatThrownBy(() -> JavaParserUtils.parse(nonExistent))
                .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid Java syntax")
        void shouldThrowForInvalidSyntax() throws IOException {
            // Given
            Path invalidFile = tempDir.resolve("Invalid.java");
            Files.writeString(invalidFile, """
                package com.example;

                public class Invalid {
                    this is not valid java syntax
                }
                """);

            // When/Then
            assertThatThrownBy(() -> JavaParserUtils.parse(invalidFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse");
        }
    }

    @Nested
    @DisplayName("findMethod(CompilationUnit, String)")
    class FindMethodTests {

        private CompilationUnit cu;

        @BeforeEach
        void setUp() throws IOException {
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, """
                package com.example;

                public class Service {
                    public void doSomething() {
                        // method body
                    }

                    public int calculate(int x) {
                        return x * 2;
                    }

                    private void helperMethod() {
                        // private helper
                    }
                }
                """);
            cu = JavaParserUtils.parse(javaFile);
        }

        @Test
        @DisplayName("should find existing method by name")
        void shouldFindExistingMethod() {
            // When
            Optional<MethodDeclaration> result = JavaParserUtils.findMethod(cu, "doSomething");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getNameAsString()).isEqualTo("doSomething");
        }

        @Test
        @DisplayName("should find method with parameters")
        void shouldFindMethodWithParameters() {
            // When
            Optional<MethodDeclaration> result = JavaParserUtils.findMethod(cu, "calculate");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getNameAsString()).isEqualTo("calculate");
            assertThat(result.get().getParameters()).hasSize(1);
        }

        @Test
        @DisplayName("should find private method")
        void shouldFindPrivateMethod() {
            // When
            Optional<MethodDeclaration> result = JavaParserUtils.findMethod(cu, "helperMethod");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getNameAsString()).isEqualTo("helperMethod");
        }

        @Test
        @DisplayName("should return empty Optional for non-existent method")
        void shouldReturnEmptyForNonExistentMethod() {
            // When
            Optional<MethodDeclaration> result = JavaParserUtils.findMethod(cu, "nonExistent");

            // Then
            assertThat(result).isEmpty();
        }
    }
}
