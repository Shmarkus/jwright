package ee.jwright.core.contract;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test class for verifying BuildTool contract compliance.
 * <p>
 * Extension developers should extend this class and implement {@link #createBuildTool()}
 * to verify their build tool implementation honors the stable contract.
 * </p>
 */
@DisplayName("BuildTool Contract")
public abstract class AbstractBuildToolTest {

    protected BuildTool buildTool;

    /**
     * Creates the build tool implementation under test.
     *
     * @return the build tool to test
     */
    protected abstract BuildTool createBuildTool();

    /**
     * Creates a path to a project that the build tool should support.
     *
     * @return path to a supported project directory
     */
    protected abstract Path createSupportedProjectPath();

    /**
     * Creates a path to a project that the build tool should NOT support.
     *
     * @return path to an unsupported project directory
     */
    protected abstract Path createUnsupportedProjectPath();

    @BeforeEach
    void setUp() {
        buildTool = createBuildTool();
    }

    @Test
    @DisplayName("getId should return non-null non-empty identifier")
    void getIdShouldReturnNonEmptyIdentifier() {
        assertThat(buildTool.getId())
            .isNotNull()
            .isNotBlank();
    }

    @Test
    @DisplayName("getOrder should return positive value within valid range")
    void getOrderShouldReturnPositiveValue() {
        assertThat(buildTool.getOrder())
            .isPositive()
            .isLessThan(10000);
    }

    @Test
    @DisplayName("supports should return true for supported project")
    void supportsShouldReturnTrueForSupportedProject() {
        var projectPath = createSupportedProjectPath();
        if (projectPath != null) {
            assertThat(buildTool.supports(projectPath)).isTrue();
        }
    }

    @Test
    @DisplayName("supports should return false for unsupported project")
    void supportsShouldReturnFalseForUnsupportedProject() {
        var projectPath = createUnsupportedProjectPath();
        if (projectPath != null) {
            assertThat(buildTool.supports(projectPath)).isFalse();
        }
    }

    @Test
    @DisplayName("compile should return non-null result")
    void compileShouldReturnNonNullResult() {
        var projectPath = createSupportedProjectPath();
        if (projectPath != null && buildTool.supports(projectPath)) {
            CompilationResult result = buildTool.compile(projectPath);
            assertThat(result).isNotNull();
            assertThat(result.errors()).isNotNull();
        }
    }

    @Test
    @DisplayName("runTests should return non-null result")
    void runTestsShouldReturnNonNullResult() {
        var projectPath = createSupportedProjectPath();
        if (projectPath != null && buildTool.supports(projectPath)) {
            TestResult result = buildTool.runTests("com.example.TestClass");
            assertThat(result).isNotNull();
            assertThat(result.failures()).isNotNull();
        }
    }

    @Test
    @DisplayName("runSingleTest should return non-null result")
    void runSingleTestShouldReturnNonNullResult() {
        var projectPath = createSupportedProjectPath();
        if (projectPath != null && buildTool.supports(projectPath)) {
            TestResult result = buildTool.runSingleTest("com.example.TestClass", "testMethod");
            assertThat(result).isNotNull();
            assertThat(result.failures()).isNotNull();
        }
    }
}
