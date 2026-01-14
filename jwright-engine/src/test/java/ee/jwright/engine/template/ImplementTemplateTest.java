package ee.jwright.engine.template;

import ee.jwright.core.extract.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the implement.mustache template.
 */
class ImplementTemplateTest {

    private MustacheTemplateEngine engine;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        // Use classpath for bundled templates
        engine = new MustacheTemplateEngine(new MustacheResolver(tempDir, null));
    }

    @Nested
    @DisplayName("7.1 implement.mustache template")
    class ImplementTemplateTests {

        @Test
        @DisplayName("renders with minimal context")
        void rendersWithMinimalContext() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "CalculatorTest");
            variables.put("testMethodName", "testAdd");
            variables.put("testMethodBody", "assertEquals(5, calculator.add(2, 3));");
            variables.put("targetMethodName", "add");
            variables.put("targetReturnType", "int");
            variables.put("targetParameters", "int a, int b");

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("CalculatorTest")
                .contains("testAdd")
                .contains("add(2, 3)")
                .contains("add")
                .contains("int");
        }

        @Test
        @DisplayName("includes test method body in prompt")
        void includesTestMethodBody() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "StringUtilTest");
            variables.put("testMethodName", "testReverse");
            variables.put("testMethodBody", """
                String input = "hello";
                String result = util.reverse(input);
                assertEquals("olleh", result);
                """);
            variables.put("targetMethodName", "reverse");
            variables.put("targetReturnType", "String");
            variables.put("targetParameters", "String input");

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("hello")
                .contains("olleh")
                .contains("reverse");
        }

        @Test
        @DisplayName("includes assertions in prompt")
        void includesAssertions() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "assertEquals(10, calc.multiply(2, 5));");
            variables.put("targetMethodName", "multiply");
            variables.put("targetReturnType", "int");
            variables.put("targetParameters", "int a, int b");
            variables.put("assertions", List.of(
                Map.of("type", "assertEquals", "expected", "10", "actual", "calc.multiply(2, 5)")
            ));

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("10")
                .contains("multiply");
        }

        @Test
        @DisplayName("includes mock setups when present")
        void includesMockSetups() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "ServiceTest");
            variables.put("testMethodName", "testGetUser");
            variables.put("testMethodBody", "when(repo.findById(1)).thenReturn(user);");
            variables.put("targetMethodName", "getUser");
            variables.put("targetReturnType", "User");
            variables.put("targetParameters", "int id");
            variables.put("mockSetups", List.of(
                Map.of("mockObject", "repo", "methodCall", "findById(1)", "returnValue", "user")
            ));
            variables.put("hasMockSetups", true);

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("repo")
                .contains("findById");
        }

        @Test
        @DisplayName("includes hints when present")
        void includesHints() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "result = impl.process();");
            variables.put("targetMethodName", "process");
            variables.put("targetReturnType", "String");
            variables.put("targetParameters", "");
            variables.put("hints", List.of("Use StringBuilder for efficiency", "Handle null input"));
            variables.put("hasHints", true);

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("StringBuilder")
                .contains("null");
        }

        @Test
        @DisplayName("includes current implementation when present")
        void includesCurrentImplementation() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "assertEquals(5, calc.add(2, 3));");
            variables.put("targetMethodName", "add");
            variables.put("targetReturnType", "int");
            variables.put("targetParameters", "int a, int b");
            variables.put("currentImplementation", "return 0; // TODO");
            variables.put("hasCurrentImplementation", true);

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("return 0")
                .contains("TODO");
        }

        @Test
        @DisplayName("includes type definitions when present")
        void includesTypeDefinitions() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "User user = service.createUser(\"John\");");
            variables.put("targetMethodName", "createUser");
            variables.put("targetReturnType", "User");
            variables.put("targetParameters", "String name");
            variables.put("typeDefinitions", List.of(
                Map.of("name", "User", "fields", List.of(
                    Map.of("name", "id", "type", "int"),
                    Map.of("name", "name", "type", "String")
                ))
            ));
            variables.put("hasTypeDefinitions", true);

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result)
                .contains("User")
                .contains("id")
                .contains("String");
        }

        @Test
        @DisplayName("instructs to return only method body")
        void instructsToReturnOnlyMethodBody() {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("testClassName", "Test");
            variables.put("testMethodName", "test");
            variables.put("testMethodBody", "assertEquals(1, impl.one());");
            variables.put("targetMethodName", "one");
            variables.put("targetReturnType", "int");
            variables.put("targetParameters", "");

            // When
            String result = engine.render("implement.mustache", variables);

            // Then
            assertThat(result.toLowerCase())
                .containsAnyOf("method body", "body only", "implementation", "code only");
        }
    }
}
