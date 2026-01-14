package ee.jwright.java.extract;

import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.TypeDefinition;
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
 * Tests for {@link JavaImplClassExtractor}.
 */
class JavaImplClassExtractorTest {

    private JavaImplClassExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaImplClassExtractor();
    }

    @Nested
    @DisplayName("supports()")
    class SupportsTests {

        @Test
        @DisplayName("returns true for existing Java impl file")
        void returnsTrue_forExistingJavaImplFile(@TempDir Path tempDir) throws IOException {
            Path implFile = tempDir.resolve("Game.java");
            Files.writeString(implFile, "public class Game {}");

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "addPlayer", null
            );

            assertThat(extractor.supports(request)).isTrue();
        }

        @Test
        @DisplayName("returns false for null impl file")
        void returnsFalse_forNullImplFile() {
            ExtractionRequest request = new ExtractionRequest(
                null, null, null, null, "addPlayer", null
            );

            assertThat(extractor.supports(request)).isFalse();
        }

        @Test
        @DisplayName("returns false for non-Java file")
        void returnsFalse_forNonJavaFile(@TempDir Path tempDir) throws IOException {
            Path implFile = tempDir.resolve("game.py");
            Files.writeString(implFile, "class Game: pass");

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "addPlayer", null
            );

            assertThat(extractor.supports(request)).isFalse();
        }

        @Test
        @DisplayName("returns false for non-existent file")
        void returnsFalse_forNonExistentFile(@TempDir Path tempDir) {
            Path implFile = tempDir.resolve("DoesNotExist.java");

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "addPlayer", null
            );

            assertThat(extractor.supports(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("extract()")
    class ExtractTests {

        @Test
        @DisplayName("extracts class fields")
        void extractsClassFields(@TempDir Path tempDir) throws IOException {
            // Given
            String implCode = """
                package org.example;

                import java.util.List;
                import java.util.ArrayList;

                public class Game {
                    private List<Player> players = new ArrayList<>();
                    private boolean started;

                    public void addPlayer(Player player) {
                        // TODO
                    }
                }
                """;
            Path implFile = tempDir.resolve("Game.java");
            Files.writeString(implFile, implCode);

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "addPlayer", null
            );
            ExtractionContext.Builder builder = ExtractionContext.builder();

            // When
            extractor.extract(request, builder);

            // Then
            ExtractionContext context = builder.build();
            assertThat(context.typeDefinitions()).hasSize(1);

            TypeDefinition gameDef = context.typeDefinitions().get(0);
            assertThat(gameDef.name()).isEqualTo("Game");
            assertThat(gameDef.fields()).containsExactlyInAnyOrder(
                "List<Player> players",
                "boolean started"
            );
        }

        @Test
        @DisplayName("extracts class methods")
        void extractsClassMethods(@TempDir Path tempDir) throws IOException {
            // Given
            String implCode = """
                package org.example;

                public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }

                    public int subtract(int a, int b) {
                        return a - b;
                    }
                }
                """;
            Path implFile = tempDir.resolve("Calculator.java");
            Files.writeString(implFile, implCode);

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "add", null
            );
            ExtractionContext.Builder builder = ExtractionContext.builder();

            // When
            extractor.extract(request, builder);

            // Then
            ExtractionContext context = builder.build();
            assertThat(context.typeDefinitions()).hasSize(1);

            TypeDefinition calcDef = context.typeDefinitions().get(0);
            assertThat(calcDef.methods()).hasSize(2);
            assertThat(calcDef.methods().get(0).name()).isEqualTo("add");
            assertThat(calcDef.methods().get(1).name()).isEqualTo("subtract");
        }

        @Test
        @DisplayName("handles class with no fields")
        void handlesClassWithNoFields(@TempDir Path tempDir) throws IOException {
            // Given
            String implCode = """
                package org.example;

                public class Greeter {
                    public String greet(String name) {
                        return "Hello, " + name;
                    }
                }
                """;
            Path implFile = tempDir.resolve("Greeter.java");
            Files.writeString(implFile, implCode);

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "greet", null
            );
            ExtractionContext.Builder builder = ExtractionContext.builder();

            // When
            extractor.extract(request, builder);

            // Then
            ExtractionContext context = builder.build();
            assertThat(context.typeDefinitions()).hasSize(1);
            assertThat(context.typeDefinitions().get(0).fields()).isEmpty();
        }

        @Test
        @DisplayName("handles generic field types correctly")
        void handlesGenericFieldTypes(@TempDir Path tempDir) throws IOException {
            // Given
            String implCode = """
                package org.example;

                import java.util.Map;
                import java.util.HashMap;

                public class Cache {
                    private Map<String, Object> data = new HashMap<>();

                    public Object get(String key) {
                        return data.get(key);
                    }
                }
                """;
            Path implFile = tempDir.resolve("Cache.java");
            Files.writeString(implFile, implCode);

            ExtractionRequest request = new ExtractionRequest(
                null, null, null, implFile, "get", null
            );
            ExtractionContext.Builder builder = ExtractionContext.builder();

            // When
            extractor.extract(request, builder);

            // Then
            ExtractionContext context = builder.build();
            TypeDefinition cacheDef = context.typeDefinitions().get(0);
            // Note: JavaParser normalizes generic types without spaces
            assertThat(cacheDef.fields()).containsExactly("Map<String,Object> data");
        }
    }

    @Nested
    @DisplayName("metadata")
    class MetadataTests {

        @Test
        @DisplayName("has correct id")
        void hasCorrectId() {
            assertThat(extractor.getId()).isEqualTo("java-impl-class");
        }

        @Test
        @DisplayName("has correct order (510)")
        void hasCorrectOrder() {
            assertThat(extractor.getOrder()).isEqualTo(510);
        }
    }
}
