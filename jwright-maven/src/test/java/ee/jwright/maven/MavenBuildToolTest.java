package ee.jwright.maven;

import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MavenBuildTool.
 */
class MavenBuildToolTest {

    private MavenBuildTool buildTool;

    @BeforeEach
    void setUp() {
        buildTool = new MavenBuildTool();
    }

    // === Task 4.1: Detection ===

    @Test
    void getId_returnsMaven() {
        assertThat(buildTool.getId()).isEqualTo("maven");
    }

    @Test
    void getOrder_returns100() {
        assertThat(buildTool.getOrder()).isEqualTo(100);
    }

    @Test
    void supports_returnsTrueWhenPomXmlExists(@TempDir Path tempDir) throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("pom.xml"));

        // Act & Assert
        assertThat(buildTool.supports(tempDir)).isTrue();
    }

    @Test
    void supports_returnsFalseWhenPomXmlDoesNotExist(@TempDir Path tempDir) {
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

    // === Task 4.2: Wrapper Detection ===

    @Test
    void getMavenCommand_returnsMvnwWhenWrapperExists(@TempDir Path tempDir) throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("mvnw"));

        // Act & Assert
        assertThat(buildTool.getMavenCommand(tempDir)).isEqualTo("./mvnw");
    }

    @Test
    void getMavenCommand_returnsMvnWhenNoWrapper(@TempDir Path tempDir) {
        // Act & Assert
        assertThat(buildTool.getMavenCommand(tempDir)).isEqualTo("mvn");
    }

    @Test
    void getMavenCommand_prefersWrapperOverSystemMaven(@TempDir Path tempDir) throws IOException {
        // Arrange - wrapper exists
        Files.createFile(tempDir.resolve("mvnw"));

        // Act & Assert - should use wrapper, not system maven
        String command = buildTool.getMavenCommand(tempDir);
        assertThat(command).isEqualTo("./mvnw");
        assertThat(command).isNotEqualTo("mvn");
    }

    // === Task 4.3: Compile ===

    @Nested
    class CompileTests {

        @Test
        void compile_returnsSuccessForValidProject(@TempDir Path tempDir) throws IOException {
            // Arrange - create a minimal valid Maven project
            createMinimalMavenProject(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        void compile_usesProjectDirectory(@TempDir Path tempDir) throws IOException {
            // Arrange - create project in temp dir
            createMinimalMavenProject(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert - should compile successfully when using correct project dir
            assertThat(result.success()).isTrue();
        }

        private void createMinimalMavenProject(Path projectDir) throws IOException {
            // Create pom.xml
            String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                    </properties>
                </project>
                """;
            Files.writeString(projectDir.resolve("pom.xml"), pomXml);

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

    // === Task 4.4: Compile Error Parsing ===

    @Nested
    class CompileErrorParsingTests {

        @Test
        void compile_returnsErrorsWhenCompilationFails(@TempDir Path tempDir) throws IOException {
            // Arrange - create a project with a compilation error
            createProjectWithCompilationError(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        void compile_extractsFilePathFromError(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithCompilationError(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.errors()).isNotEmpty();
            CompilationError error = result.errors().get(0);
            assertThat(error.file().toString()).contains("BrokenClass.java");
        }

        @Test
        void compile_extractsLineNumberFromError(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithCompilationError(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.errors()).isNotEmpty();
            CompilationError error = result.errors().get(0);
            assertThat(error.line()).isGreaterThan(0);
        }

        @Test
        void compile_extractsErrorMessage(@TempDir Path tempDir) throws IOException {
            // Arrange
            createProjectWithCompilationError(tempDir);

            // Act
            CompilationResult result = buildTool.compile(tempDir);

            // Assert
            assertThat(result.errors()).isNotEmpty();
            CompilationError error = result.errors().get(0);
            assertThat(error.message()).isNotBlank();
        }

        private void createProjectWithCompilationError(Path projectDir) throws IOException {
            // Create pom.xml
            String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                    </properties>
                </project>
                """;
            Files.writeString(projectDir.resolve("pom.xml"), pomXml);

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

    // === Task 4.5: Run Test Class ===

    @Nested
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
            // Create pom.xml with JUnit dependency and surefire plugin
            String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
            Files.writeString(projectDir.resolve("pom.xml"), pomXml);

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

    // === Task 4.6: Run Single Test ===

    @Nested
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
            // Create pom.xml with JUnit dependency
            String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
            Files.writeString(projectDir.resolve("pom.xml"), pomXml);

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

    // === Task 4.7: Test Failure Parsing ===

    @Nested
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
            assertThat(result.failures()).isNotEmpty();
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
            assertThat(failure.testMethod()).isEqualTo("shouldFail");
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
            // Create pom.xml with JUnit dependency
            String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
            Files.writeString(projectDir.resolve("pom.xml"), pomXml);

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
