package ee.jwright.engine.resolve;

import ee.jwright.core.exception.JwrightException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTargetResolverTest {

    @Test
    void resolve_withSimpleTarget_returnsCorrectPaths(@TempDir Path projectDir) throws JwrightException, IOException {
        // Arrange - Create test file in default package
        Path testDir = projectDir.resolve("src/test/java");
        Files.createDirectories(testDir);
        Path testFile = testDir.resolve("GameTest.java");
        Files.writeString(testFile, "class GameTest {}");

        TestTargetResolver resolver = new TestTargetResolver();
        String target = "GameTest#testAdd";

        // Act
        TestTargetResolver.ResolvedTarget result = resolver.resolve(projectDir, target);

        // Assert
        assertThat(result.testFile()).isEqualTo(projectDir.resolve("src/test/java/GameTest.java"));
        assertThat(result.testClassName()).isEqualTo("GameTest");
        assertThat(result.testMethodName()).isEqualTo("testAdd");
        assertThat(result.implFile()).isEqualTo(projectDir.resolve("src/main/java/Game.java"));
        assertThat(result.implClassName()).isEqualTo("Game");
        assertThat(result.sourceRoot()).isEqualTo(projectDir.resolve("src/main/java"));
    }

    @Test
    void resolve_withFullyQualifiedTarget_handlesPackagePath(@TempDir Path projectDir) throws JwrightException {
        // Arrange
        TestTargetResolver resolver = new TestTargetResolver();
        String target = "org.example.GameTest#testAdd";

        // Act
        TestTargetResolver.ResolvedTarget result = resolver.resolve(projectDir, target);

        // Assert
        assertThat(result.testFile()).isEqualTo(projectDir.resolve("src/test/java/org/example/GameTest.java"));
        assertThat(result.testClassName()).isEqualTo("GameTest");
        assertThat(result.testMethodName()).isEqualTo("testAdd");
        assertThat(result.implFile()).isEqualTo(projectDir.resolve("src/main/java/org/example/Game.java"));
        assertThat(result.implClassName()).isEqualTo("Game");
        assertThat(result.sourceRoot()).isEqualTo(projectDir.resolve("src/main/java"));
    }

    @Test
    void resolve_withMissingHash_throwsDescriptiveError(@TempDir Path projectDir) {
        // Arrange
        TestTargetResolver resolver = new TestTargetResolver();
        String target = "GameTest";

        // Act & Assert
        assertThatThrownBy(() -> resolver.resolve(projectDir, target))
            .isInstanceOf(JwrightException.class)
            .hasMessageContaining("Invalid target format")
            .hasMessageContaining("Expected format: ClassName#methodName");
    }

    @Test
    void resolve_derivesImplClassFromTestClass(@TempDir Path projectDir) throws JwrightException, IOException {
        // Arrange - Create test files in default package
        Path testDir = projectDir.resolve("src/test/java");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("CalculatorTest.java"), "class CalculatorTest {}");
        Files.writeString(testDir.resolve("UserServiceTest.java"), "class UserServiceTest {}");

        TestTargetResolver resolver = new TestTargetResolver();

        // Act & Assert - CalculatorTest -> Calculator
        TestTargetResolver.ResolvedTarget result1 = resolver.resolve(projectDir, "CalculatorTest#testAdd");
        assertThat(result1.implClassName()).isEqualTo("Calculator");

        // Act & Assert - UserServiceTest -> UserService
        TestTargetResolver.ResolvedTarget result2 = resolver.resolve(projectDir, "UserServiceTest#testCreate");
        assertThat(result2.implClassName()).isEqualTo("UserService");
    }

    @Test
    void resolve_derivesMethodNameFromTestMethod(@TempDir Path projectDir) throws JwrightException, IOException {
        // Arrange - Create test file in default package
        Path testDir = projectDir.resolve("src/test/java");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("GameTest.java"), "class GameTest {}");

        TestTargetResolver resolver = new TestTargetResolver();

        // Act & Assert - testAdd_returnsSum -> add
        TestTargetResolver.ResolvedTarget result1 = resolver.resolve(projectDir, "GameTest#testAdd_returnsSum");
        assertThat(result1.implMethodName()).isEqualTo("add");

        // Act & Assert - addPlayer_addsPlayer -> addPlayer (no "test" prefix)
        TestTargetResolver.ResolvedTarget result2 = resolver.resolve(projectDir, "GameTest#addPlayer_addsPlayer");
        assertThat(result2.implMethodName()).isEqualTo("addPlayer");

        // Act & Assert - testSimple -> simple
        TestTargetResolver.ResolvedTarget result3 = resolver.resolve(projectDir, "GameTest#testSimple");
        assertThat(result3.implMethodName()).isEqualTo("simple");
    }

    @Nested
    class SimpleClassNameResolution {

        @TempDir
        Path projectDir;

        private TestTargetResolver resolver;

        @BeforeEach
        void setUp() {
            resolver = new TestTargetResolver();
        }

        @Test
        void resolve_withSimpleClassName_findsTestInSubpackage() throws JwrightException, IOException {
            // Arrange - Create test file in a package
            Path testDir = projectDir.resolve("src/test/java/com/example");
            Files.createDirectories(testDir);
            Path testFile = testDir.resolve("CalculatorTest.java");
            Files.writeString(testFile, "package com.example; class CalculatorTest {}");

            // Act
            TestTargetResolver.ResolvedTarget result = resolver.resolve(projectDir, "CalculatorTest#testAdd");

            // Assert
            assertThat(result.testFile()).isEqualTo(testFile);
            assertThat(result.testClassName()).isEqualTo("CalculatorTest");
            assertThat(result.testMethodName()).isEqualTo("testAdd");
            assertThat(result.implFile()).isEqualTo(projectDir.resolve("src/main/java/com/example/Calculator.java"));
            assertThat(result.implClassName()).isEqualTo("Calculator");
        }

        @Test
        void resolve_withSimpleClassName_findsTestInDeeplyNestedPackage() throws JwrightException, IOException {
            // Arrange - Create test file in deeply nested package
            Path testDir = projectDir.resolve("src/test/java/org/example/math/operations");
            Files.createDirectories(testDir);
            Path testFile = testDir.resolve("CalculatorTest.java");
            Files.writeString(testFile, "package org.example.math.operations; class CalculatorTest {}");

            // Act
            TestTargetResolver.ResolvedTarget result = resolver.resolve(projectDir, "CalculatorTest#testAdd");

            // Assert
            assertThat(result.testFile()).isEqualTo(testFile);
            assertThat(result.implFile()).isEqualTo(projectDir.resolve("src/main/java/org/example/math/operations/Calculator.java"));
        }

        @Test
        void resolve_withFullyQualifiedClassName_doesNotSearch() throws JwrightException, IOException {
            // Arrange - Create test file in different location than specified
            Path testDir = projectDir.resolve("src/test/java/other/package");
            Files.createDirectories(testDir);
            Path actualFile = testDir.resolve("CalculatorTest.java");
            Files.writeString(actualFile, "package other.pkg; class CalculatorTest {}");

            // Act - Using fully qualified name should NOT search, just use the path as-is
            TestTargetResolver.ResolvedTarget result = resolver.resolve(projectDir, "com.example.CalculatorTest#testAdd");

            // Assert - Should resolve to specified path (not search)
            assertThat(result.testFile()).isEqualTo(projectDir.resolve("src/test/java/com/example/CalculatorTest.java"));
        }

        @Test
        void resolve_withSimpleClassName_throwsOnMultipleMatches() throws IOException {
            // Arrange - Create same test class in two different packages
            Path testDir1 = projectDir.resolve("src/test/java/com/example");
            Path testDir2 = projectDir.resolve("src/test/java/org/other");
            Files.createDirectories(testDir1);
            Files.createDirectories(testDir2);
            Files.writeString(testDir1.resolve("CalculatorTest.java"), "package com.example; class CalculatorTest {}");
            Files.writeString(testDir2.resolve("CalculatorTest.java"), "package org.other; class CalculatorTest {}");

            // Act & Assert
            assertThatThrownBy(() -> resolver.resolve(projectDir, "CalculatorTest#testAdd"))
                .isInstanceOf(JwrightException.class)
                .hasMessageContaining("Ambiguous")
                .hasMessageContaining("CalculatorTest")
                .hasMessageContaining("com/example")
                .hasMessageContaining("org/other");
        }

        @Test
        void resolve_withSimpleClassName_throwsOnNoMatches() throws IOException {
            // Arrange - Empty test source directory
            Path testDir = projectDir.resolve("src/test/java");
            Files.createDirectories(testDir);

            // Act & Assert
            assertThatThrownBy(() -> resolver.resolve(projectDir, "NonExistentTest#testMethod"))
                .isInstanceOf(JwrightException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining("NonExistentTest");
        }

        @Test
        void resolve_withSimpleClassName_ignoresNonTestDirectories() throws JwrightException, IOException {
            // Arrange - Create file with same name in main (should be ignored)
            Path mainDir = projectDir.resolve("src/main/java/com/example");
            Path testDir = projectDir.resolve("src/test/java/org/other");
            Files.createDirectories(mainDir);
            Files.createDirectories(testDir);
            Files.writeString(mainDir.resolve("CalculatorTest.java"), "package com.example; class CalculatorTest {}");
            Files.writeString(testDir.resolve("CalculatorTest.java"), "package org.other; class CalculatorTest {}");

            // Act - Should only find the one in test directory
            TestTargetResolver.ResolvedTarget result = resolver.resolve(projectDir, "CalculatorTest#testAdd");

            // Assert
            assertThat(result.testFile()).isEqualTo(testDir.resolve("CalculatorTest.java"));
        }
    }
}
