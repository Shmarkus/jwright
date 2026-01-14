package ee.jwright.engine.resolve;

import ee.jwright.core.exception.JwrightException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTargetResolverTest {

    @Test
    void resolve_withSimpleTarget_returnsCorrectPaths(@TempDir Path projectDir) throws JwrightException {
        // Arrange
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
    void resolve_derivesImplClassFromTestClass(@TempDir Path projectDir) throws JwrightException {
        // Arrange
        TestTargetResolver resolver = new TestTargetResolver();

        // Act & Assert - CalculatorTest -> Calculator
        TestTargetResolver.ResolvedTarget result1 = resolver.resolve(projectDir, "CalculatorTest#testAdd");
        assertThat(result1.implClassName()).isEqualTo("Calculator");

        // Act & Assert - UserServiceTest -> UserService
        TestTargetResolver.ResolvedTarget result2 = resolver.resolve(projectDir, "UserServiceTest#testCreate");
        assertThat(result2.implClassName()).isEqualTo("UserService");
    }

    @Test
    void resolve_derivesMethodNameFromTestMethod(@TempDir Path projectDir) throws JwrightException {
        // Arrange
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
}
