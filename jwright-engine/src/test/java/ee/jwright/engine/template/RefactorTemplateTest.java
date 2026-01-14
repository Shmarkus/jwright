package ee.jwright.engine.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the refactor.mustache template.
 */
class RefactorTemplateTest {

    private MustacheTemplateEngine engine;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        // Use classpath for bundled templates
        engine = new MustacheTemplateEngine(new MustacheResolver(tempDir, null));
    }

    @Nested
    @DisplayName("7.6 refactor.mustache template")
    class RefactorTemplateTests {

        @Test
        @DisplayName("renders with generated code")
        void rendersWithGeneratedCode() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("generatedCode", "return a + b;");
            variables.put("testClassName", "CalculatorTest");
            variables.put("testMethodName", "testAdd");
            variables.put("testMethodBody", "assertEquals(5, calculator.add(2, 3));");

            // When
            String result = engine.render("refactor.mustache", variables);

            // Then
            assertThat(result)
                .contains("return a + b;")
                .contains("CalculatorTest")
                .contains("testAdd");
        }

        @Test
        @DisplayName("includes refactoring instructions")
        void includesRefactoringInstructions() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("generatedCode", "int x = a; int y = b; return x + y;");
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "assertEquals(5, calc.add(2, 3));");

            // When
            String result = engine.render("refactor.mustache", variables);

            // Then
            assertThat(result.toLowerCase())
                .containsAnyOf("refactor", "clean", "improve", "simplify");
        }

        @Test
        @DisplayName("instructs to keep tests passing")
        void instructsToKeepTestsPassing() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("generatedCode", "return a + b;");
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "assertEquals(5, calc.add(2, 3));");

            // When
            String result = engine.render("refactor.mustache", variables);

            // Then
            assertThat(result.toLowerCase())
                .containsAnyOf("test", "pass", "behavior", "functionality");
        }

        @Test
        @DisplayName("instructs to return only method body")
        void instructsToReturnOnlyMethodBody() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("generatedCode", "return a + b;");
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "assertEquals(1, impl.one());");

            // When
            String result = engine.render("refactor.mustache", variables);

            // Then
            assertThat(result.toLowerCase())
                .containsAnyOf("method body", "body only", "implementation", "code only");
        }
    }
}
