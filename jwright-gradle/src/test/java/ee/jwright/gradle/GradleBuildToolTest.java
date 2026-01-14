package ee.jwright.gradle;

import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GradleBuildTool.
 */
class GradleBuildToolTest {

    private GradleBuildTool buildTool;

    @BeforeEach
    void setUp() {
        buildTool = new GradleBuildTool();
    }

    /**
     * System property to enable Gradle integration tests.
     * Run with: mvn test -Djwright.integration.gradle=true
     */
    static final String INTEGRATION_TEST_PROPERTY = "jwright.integration.gradle";

    // === Detection Tests ===

    @Test
    void getId_returnsGradle() {
        assertThat(buildTool.getId()).isEqualTo("gradle");
    }

    @Test
    void getOrder_returns200() {
        assertThat(buildTool.getOrder()).isEqualTo(200);
    }

    @Test
    void supports_returnsTrueWhenBuildGradleExists(@TempDir Path tempDir) throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("build.gradle"));

        // Act & Assert
        assertThat(buildTool.supports(tempDir)).isTrue();
    }

    @Test
    void supports_returnsTrueWhenBuildGradleKtsExists(@TempDir Path tempDir) throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("build.gradle.kts"));

        // Act & Assert
        assertThat(buildTool.supports(tempDir)).isTrue();
    }

    @Test
    void supports_returnsFalseWhenNoBuildFile(@TempDir Path tempDir) {
        // Act & Assert
        assertThat(buildTool.supports(tempDir)).isFalse();
    }

    @Test
    void supports_returnsFalseWhenDirectoryDoesNotExist() {
        // Arrange
        Path nonExistent = Path.of("/non/existent/directory");

        // Act & Assert
        assertThat(buildTool.supports(nonExistent)).isFalse();
    }

    // === Wrapper Detection Tests ===

    @Test
    void getGradleCommand_returnsGradlewWhenWrapperExists(@TempDir Path tempDir) throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("gradlew"));

        // Act & Assert
        assertThat(buildTool.getGradleCommand(tempDir)).isEqualTo("./gradlew");
    }

    @Test
    void getGradleCommand_returnsGradleWhenNoWrapper(@TempDir Path tempDir) {
        // Act & Assert
        assertThat(buildTool.getGradleCommand(tempDir)).isEqualTo("gradle");
    }

    @Test
    void getGradleCommand_prefersWrapperOverSystemGradle(@TempDir Path tempDir) throws IOException {
        // Arrange - wrapper exists
        Files.createFile(tempDir.resolve("gradlew"));

        // Act & Assert - should use wrapper, not system gradle
        String command = buildTool.getGradleCommand(tempDir);
        assertThat(command).isEqualTo("./gradlew");
        assertThat(command).isNotEqualTo("gradle");
    }

    // === Compile Tests (Integration) ===

    @Nested
    @EnabledIfSystemProperty(named = "jwright.integration.gradle", matches = "true")
    class CompileTests {

        @Test
        void compile_returnsSuccessForValidProject(@TempDir Path tempDir) throws IOException {
            // Arrange - create a minimal valid Gradle project
            createMinimalGradleProject(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        void compile_usesProjectDirectory(@TempDir Path tempDir) throws IOException {
            // Arrange - create project in temp dir
            createMinimalGradleProject(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert - should compile successfully when using correct project dir
            assertThat(result.success()).isTrue();
        }

        private void createMinimalGradleProject(Path projectDir) throws IOException {
            // Create build.gradle
            String buildGradle = """
                plugins {
                    id 'java'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                repositories {
                    mavenCentral()
                }
                """;
            Files.writeString(projectDir.resolve("build.gradle"), buildGradle);

            // Create settings.gradle
            Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'");

            // Create src/main/java directory structure
            Path srcMainJava = projectDir.resolve("src/main/java/test");
            Files.createDirectories(srcMainJava);

            // Create a simple Java class
            String javaClass = """
                package test;

                public class TestClass {
                    public String hello() {
                        return "Hello";
                    }
                }
                """;
            Files.writeString(srcMainJava.resolve("TestClass.java"), javaClass);
        }
    }

    // === Compile Error Parsing Tests (Integration) ===

    @Nested
    @EnabledIfSystemProperty(named = "jwright.integration.gradle", matches = "true")
    class CompileErrorParsingTests {

        @Test
        void compile_returnsErrorsWhenCompilationFails(@TempDir Path tempDir) throws IOException {
            // Arrange - create a project with a compilation error
            createProjectWithCompilationError(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.success()).isFalse();
        }

        private void createProjectWithCompilationError(Path projectDir) throws IOException {
            // Create build.gradle
            String buildGradle = """
                plugins {
                    id 'java'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                repositories {
                    mavenCentral()
                }
                """;
            Files.writeString(projectDir.resolve("build.gradle"), buildGradle);

            // Create settings.gradle
            Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'");

            // Create src/main/java directory structure
            Path srcMainJava = projectDir.resolve("src/main/java/test");
            Files.createDirectories(srcMainJava);

            // Create a Java class with a syntax error
            String brokenJavaClass = """
                package test;

                public class BrokenClass {
                    public String hello() {
                        return undefinedVariable;  // This will cause a compilation error
                    }
                }
                """;
            Files.writeString(srcMainJava.resolve("BrokenClass.java"), brokenJavaClass);
        }
    }

    // === Run Test Class Tests (Integration) ===

    @Nested
    @EnabledIfSystemProperty(named = "jwright.integration.gradle", matches = "true")
    class RunTestClassTests {

        @Test
        void runTests_returnsSuccessForPassingTestClass(@TempDir Path tempDir) throws IOException {
            // Arrange - create a project with a passing test
            createProjectWithPassingTest(tempDir);
            buildTool.compile(tempDir);  // Need to compile first

            // Act
            TestResult result = buildTool.runTests("test.SampleTest");

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.passed()).isGreaterThan(0);
            assertThat(result.failed()).isZero();
        }

        @Test
        void runTests_returnsCorrectPassedCount(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithPassingTest(tempDir);
            buildTool.compile(tempDir);

            // Act
            TestResult result = buildTool.runTests("test.SampleTest");

            // Assert
            assertThat(result.passed()).isEqualTo(1);  // We have one test method
        }

        private void createProjectWithPassingTest(Path projectDir) throws IOException {
            // Create build.gradle with JUnit dependency
            String buildGradle = """
                plugins {
                    id 'java'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
                }

                test {
                    useJUnitPlatform()
                }
                """;
            Files.writeString(projectDir.resolve("build.gradle"), buildGradle);

            // Create settings.gradle
            Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'");

            // Create src/test/java directory structure
            Path srcTestJava = projectDir.resolve("src/test/java/test");
            Files.createDirectories(srcTestJava);

            // Create a passing test class
            String testClass = """
                package test;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                public class SampleTest {
                    @Test
                    void shouldPass() {
                        assertTrue(true);
                    }
                }
                """;
            Files.writeString(srcTestJava.resolve("SampleTest.java"), testClass);
        }
    }

    // === Run Single Test Tests (Integration) ===

    @Nested
    @EnabledIfSystemProperty(named = "jwright.integration.gradle", matches = "true")
    class RunSingleTestTests {

        @Test
        void runSingleTest_returnsSuccessForPassingTest(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithMultipleTests(tempDir);
            buildTool.compile(tempDir);

            // Act
            TestResult result = buildTool.runSingleTest("test.MultiTest", "shouldPass");

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isZero();
        }

        @Test
        void runSingleTest_runsOnlySpecifiedMethod(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithMultipleTests(tempDir);
            buildTool.compile(tempDir);

            // Act - run only one method from a class with multiple tests
            TestResult result = buildTool.runSingleTest("test.MultiTest", "shouldPass");

            // Assert - should run only 1 test, not all tests in the class
            assertThat(result.passed()).isEqualTo(1);
        }

        private void createProjectWithMultipleTests(Path projectDir) throws IOException {
            // Create build.gradle with JUnit dependency
            String buildGradle = """
                plugins {
                    id 'java'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
                }

                test {
                    useJUnitPlatform()
                }
                """;
            Files.writeString(projectDir.resolve("build.gradle"), buildGradle);

            // Create settings.gradle
            Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'");

            // Create src/test/java directory structure
            Path srcTestJava = projectDir.resolve("src/test/java/test");
            Files.createDirectories(srcTestJava);

            // Create a test class with multiple test methods
            String testClass = """
                package test;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                public class MultiTest {
                    @Test
                    void shouldPass() {
                        assertTrue(true);
                    }

                    @Test
                    void anotherTest() {
                        assertTrue(true);
                    }
                }
                """;
            Files.writeString(srcTestJava.resolve("MultiTest.java"), testClass);
        }
    }

    // === Test Failure Parsing Tests (Integration) ===

    @Nested
    @EnabledIfSystemProperty(named = "jwright.integration.gradle", matches = "true")
    class TestFailureParsingTests {

        @Test
        void runTests_returnsFailuresWhenTestFails(@TempDir Path tempDir) throws IOException {
            // Arrange - create a project with a failing test
            createProjectWithFailingTest(tempDir);
            buildTool.compile(tempDir);

            // Act
            TestResult result = buildTool.runTests("test.FailingTest");

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.failed()).isGreaterThan(0);
        }

        @Test
        void runTests_extractsTestClassFromFailure(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithFailingTest(tempDir);
            buildTool.compile(tempDir);

            // Act
            TestResult result = buildTool.runTests("test.FailingTest");

            // Assert
            assertThat(result.failures()).isNotEmpty();
            TestFailure failure = result.failures().get(0);
            assertThat(failure.testClass()).isEqualTo("test.FailingTest");
        }

        @Test
        void runTests_extractsTestMethodFromFailure(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithFailingTest(tempDir);
            buildTool.compile(tempDir);

            // Act
            TestResult result = buildTool.runTests("test.FailingTest");

            // Assert
            assertThat(result.failures()).isNotEmpty();
            TestFailure failure = result.failures().get(0);
            assertThat(failure.testMethod()).isEqualTo("shouldFail()");
        }

        @Test
        void runTests_extractsFailureMessage(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithFailingTest(tempDir);
            buildTool.compile(tempDir);

            // Act
            TestResult result = buildTool.runTests("test.FailingTest");

            // Assert
            assertThat(result.failures()).isNotEmpty();
            TestFailure failure = result.failures().get(0);
            assertThat(failure.message()).isNotBlank();
        }

        private void createProjectWithFailingTest(Path projectDir) throws IOException {
            // Create build.gradle with JUnit dependency
            String buildGradle = """
                plugins {
                    id 'java'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
                }

                test {
                    useJUnitPlatform()
                }
                """;
            Files.writeString(projectDir.resolve("build.gradle"), buildGradle);

            // Create settings.gradle
            Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'");

            // Create src/test/java directory structure
            Path srcTestJava = projectDir.resolve("src/test/java/test");
            Files.createDirectories(srcTestJava);

            // Create a failing test class
            String testClass = """
                package test;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;

                public class FailingTest {
                    @Test
                    void shouldFail() {
                        fail("Intentional failure");
                    }
                }
                """;
            Files.writeString(srcTestJava.resolve("FailingTest.java"), testClass);
        }
    }
}
