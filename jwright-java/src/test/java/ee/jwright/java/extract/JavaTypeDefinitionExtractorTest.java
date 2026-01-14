package ee.jwright.java.extract;

import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.TypeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaTypeDefinitionExtractor}.
 */
class JavaTypeDefinitionExtractorTest {

    @TempDir
    Path tempDir;

    private JavaTypeDefinitionExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaTypeDefinitionExtractor();
    }

    @Test
    @DisplayName("should have id 'java-type-definition'")
    void shouldHaveCorrectId() {
        assertThat(extractor.getId()).isEqualTo("java-type-definition");
    }

    @Test
    @DisplayName("should have order 600 (type definitions range)")
    void shouldHaveCorrectOrder() {
        assertThat(extractor.getOrder()).isEqualTo(600);
    }

    @Test
    @DisplayName("should support .java files")
    void shouldSupportJavaFiles() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "class Test {}");
        ExtractionRequest request = createRequest(testFile);

        assertThat(extractor.supports(request)).isTrue();
    }

    @Test
    @DisplayName("should extract type from variable declaration")
    void shouldExtractTypeFromVariableDeclaration() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class PersonTest {
                @Test
                void shouldCreatePerson() {
                    Person person = new Person("John", 30);
                    assertEquals("John", person.getName());
                }
            }
            """);

        // Create a Person class in the same directory to simulate source root
        Path personFile = tempDir.resolve("Person.java");
        Files.writeString(personFile, """
            package com.example;

            public class Person {
                private String name;
                private int age;

                public Person(String name, int age) {
                    this.name = name;
                    this.age = age;
                }

                public String getName() {
                    return name;
                }

                public int getAge() {
                    return age;
                }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "PersonTest", "shouldCreatePerson", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.typeDefinitions()).isNotEmpty();
        TypeDefinition personType = context.typeDefinitions().stream()
            .filter(t -> t.name().contains("Person"))
            .findFirst()
            .orElse(null);
        assertThat(personType).isNotNull();
        assertThat(personType.fields()).contains("String name", "int age");
    }

    @Test
    @DisplayName("should extract type from method return type")
    void shouldExtractTypeFromReturnType() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class ServiceTest {
                @Test
                void shouldReturnResult() {
                    Service service = new Service();
                    Result result = service.process();
                    assertNotNull(result);
                }
            }
            """);

        Path resultFile = tempDir.resolve("Result.java");
        Files.writeString(resultFile, """
            package com.example;

            public class Result {
                private boolean success;
                private String message;

                public boolean isSuccess() { return success; }
                public String getMessage() { return message; }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "ServiceTest", "shouldReturnResult", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.typeDefinitions()).isNotEmpty();
        TypeDefinition resultType = context.typeDefinitions().stream()
            .filter(t -> t.name().contains("Result"))
            .findFirst()
            .orElse(null);
        assertThat(resultType).isNotNull();
    }

    @Test
    @DisplayName("should extract record type")
    void shouldExtractRecordType() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class OrderTest {
                @Test
                void shouldCreateOrder() {
                    Order order = new Order(1L, "item", 100);
                    assertEquals(1L, order.id());
                }
            }
            """);

        Path orderFile = tempDir.resolve("Order.java");
        Files.writeString(orderFile, """
            package com.example;

            public record Order(long id, String item, int quantity) {}
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "OrderTest", "shouldCreateOrder", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.typeDefinitions()).isNotEmpty();
        TypeDefinition orderType = context.typeDefinitions().stream()
            .filter(t -> t.name().contains("Order"))
            .findFirst()
            .orElse(null);
        assertThat(orderType).isNotNull();
    }

    @Test
    @DisplayName("should handle missing type file gracefully")
    void shouldHandleMissingTypeFileGracefully() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class UnknownTest {
                @Test
                void shouldUseUnknownType() {
                    UnknownType unknown = new UnknownType();
                }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "UnknownTest", "shouldUseUnknownType", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then - should not throw and should complete gracefully
        assertThat(context.typeDefinitions()).isEmpty();
    }

    @Test
    @DisplayName("should skip primitive types")
    void shouldSkipPrimitiveTypes() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class PrimitiveTest {
                @Test
                void shouldUsePrimitives() {
                    int a = 5;
                    long b = 10L;
                    double c = 3.14;
                    boolean d = true;
                }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "PrimitiveTest", "shouldUsePrimitives", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then - primitives should not be extracted as type definitions
        assertThat(context.typeDefinitions()).isEmpty();
    }

    private Path createTestFile(String content) throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, content);
        return testFile;
    }

    private ExtractionRequest createRequest(Path testFile) {
        return new ExtractionRequest(testFile, "Test", "test", null, null, tempDir);
    }
}
