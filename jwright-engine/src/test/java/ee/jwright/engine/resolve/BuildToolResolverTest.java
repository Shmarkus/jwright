package ee.jwright.engine.resolve;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.exception.JwrightException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BuildToolResolver}.
 */
class BuildToolResolverTest {

    @Test
    @DisplayName("resolve() returns build tool that supports the project")
    void resolveReturnsSupportingBuildTool(@TempDir Path tempDir) throws IOException, JwrightException {
        // Given - create a Gradle project
        Files.writeString(tempDir.resolve("build.gradle"), "");

        BuildTool mavenTool = createMockBuildTool("maven", 100, false);
        BuildTool gradleTool = createMockBuildTool("gradle", 200, true);

        BuildToolResolver resolver = new BuildToolResolver(List.of(mavenTool, gradleTool));

        // When
        BuildTool result = resolver.resolve(tempDir);

        // Then
        assertThat(result.getId()).isEqualTo("gradle");
    }

    @Test
    @DisplayName("resolve() prefers build tool with lower order when multiple support")
    void resolvePrefersLowerOrder(@TempDir Path tempDir) throws JwrightException {
        // Given - both tools support the project
        BuildTool tool1 = createMockBuildTool("tool1", 200, true);
        BuildTool tool2 = createMockBuildTool("tool2", 100, true);

        BuildToolResolver resolver = new BuildToolResolver(List.of(tool1, tool2));

        // When
        BuildTool result = resolver.resolve(tempDir);

        // Then - tool2 has lower order (100) so it should be preferred
        assertThat(result.getId()).isEqualTo("tool2");
    }

    @Test
    @DisplayName("resolve() throws when no build tool supports project")
    void resolveThrowsWhenNoSupport(@TempDir Path tempDir) {
        // Given
        BuildTool tool = createMockBuildTool("unsupported", 100, false);

        BuildToolResolver resolver = new BuildToolResolver(List.of(tool));

        // When/Then
        assertThatThrownBy(() -> resolver.resolve(tempDir))
            .isInstanceOf(JwrightException.class)
            .hasMessageContaining("No build tool found");
    }

    @Test
    @DisplayName("resolve() throws when build tools list is empty")
    void resolveThrowsWhenNoBuildTools(@TempDir Path tempDir) {
        // Given
        BuildToolResolver resolver = new BuildToolResolver(List.of());

        // When/Then
        assertThatThrownBy(() -> resolver.resolve(tempDir))
            .isInstanceOf(JwrightException.class)
            .hasMessageContaining("No build tool found");
    }

    @Test
    @DisplayName("tryResolve() returns Optional.empty when no build tool supports")
    void tryResolveReturnsEmptyOptional(@TempDir Path tempDir) {
        // Given
        BuildTool tool = createMockBuildTool("unsupported", 100, false);

        BuildToolResolver resolver = new BuildToolResolver(List.of(tool));

        // When
        Optional<BuildTool> result = resolver.tryResolve(tempDir);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tryResolve() returns matching build tool")
    void tryResolveReturnsMatchingTool(@TempDir Path tempDir) {
        // Given
        BuildTool tool = createMockBuildTool("matching", 100, true);

        BuildToolResolver resolver = new BuildToolResolver(List.of(tool));

        // When
        Optional<BuildTool> result = resolver.tryResolve(tempDir);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("matching");
    }

    private BuildTool createMockBuildTool(String id, int order, boolean supports) {
        BuildTool tool = mock(BuildTool.class);
        when(tool.getId()).thenReturn(id);
        when(tool.getOrder()).thenReturn(order);
        when(tool.supports(org.mockito.ArgumentMatchers.any())).thenReturn(supports);
        return tool;
    }
}
