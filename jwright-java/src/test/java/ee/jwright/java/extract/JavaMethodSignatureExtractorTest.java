package ee.jwright.java.extract;

import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaMethodSignatureExtractor}.
 */
class JavaMethodSignatureExtractorTest {

    @TempDir
    Path tempDir;

    private JavaMethodSignatureExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JavaMethodSignatureExtractor();
    }

    @Test
    @DisplayName("should have id 'java-method-signature'")
    void shouldHaveCorrectId() {
        assertThat(extractor.getId()).isEqualTo("java-method-signature");
    }

    @Test
    @DisplayName("should have order 700 (method signatures range)")
    void shouldHaveCorrectOrder() {
        assertThat(extractor.getOrder()).isEqualTo(700);
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
    @DisplayName("should extract methods from collaborator class")
    void shouldExtractMethodsFromCollaborator() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class ServiceTest {
                @Test
                void shouldUseRepository() {
                    Repository repo = new Repository();
                    repo.findById(1L);
                    repo.save(entity);
                }
            }
            """);

        Path repoFile = tempDir.resolve("Repository.java");
        Files.writeString(repoFile, """
            package com.example;

            public class Repository {
                public Entity findById(long id) { return null; }
                public void save(Entity entity) {}
                public List<Entity> findAll() { return null; }
                public void delete(long id) {}
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "ServiceTest", "shouldUseRepository", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.availableMethods()).containsKey("Repository");
        List<MethodSignature> methods = context.availableMethods().get("Repository");
        assertThat(methods).isNotEmpty();
        assertThat(methods.stream().map(MethodSignature::name))
            .contains("findById", "save", "findAll", "delete");
    }

    @Test
    @DisplayName("should extract method parameters")
    void shouldExtractMethodParameters() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class CalculatorTest {
                @Test
                void shouldCalculate() {
                    Calculator calc = new Calculator();
                    calc.add(1, 2);
                }
            }
            """);

        Path calcFile = tempDir.resolve("Calculator.java");
        Files.writeString(calcFile, """
            package com.example;

            public class Calculator {
                public int add(int a, int b) { return a + b; }
                public int subtract(int minuend, int subtrahend) { return 0; }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "CalculatorTest", "shouldCalculate", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        List<MethodSignature> methods = context.availableMethods().get("Calculator");
        assertThat(methods).isNotNull();

        MethodSignature addMethod = methods.stream()
            .filter(m -> m.name().equals("add"))
            .findFirst()
            .orElse(null);
        assertThat(addMethod).isNotNull();
        assertThat(addMethod.returnType()).isEqualTo("int");
        assertThat(addMethod.parameters()).hasSize(2);
    }

    @Test
    @DisplayName("should extract methods from multiple collaborators")
    void shouldExtractMethodsFromMultipleCollaborators() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class ServiceTest {
                @Test
                void shouldUseMultipleCollaborators() {
                    UserRepo userRepo = new UserRepo();
                    OrderRepo orderRepo = new OrderRepo();
                    userRepo.findUser(1L);
                    orderRepo.findOrders(1L);
                }
            }
            """);

        Path userRepoFile = tempDir.resolve("UserRepo.java");
        Files.writeString(userRepoFile, """
            package com.example;

            public class UserRepo {
                public User findUser(long id) { return null; }
            }
            """);

        Path orderRepoFile = tempDir.resolve("OrderRepo.java");
        Files.writeString(orderRepoFile, """
            package com.example;

            public class OrderRepo {
                public List<Order> findOrders(long userId) { return null; }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "ServiceTest", "shouldUseMultipleCollaborators", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then
        assertThat(context.availableMethods()).containsKeys("UserRepo", "OrderRepo");
    }

    @Test
    @DisplayName("should handle missing collaborator file gracefully")
    void shouldHandleMissingCollaboratorGracefully() throws IOException {
        // Given
        Path testFile = createTestFile("""
            package com.example;

            import org.junit.jupiter.api.Test;

            class ServiceTest {
                @Test
                void shouldUseMissingRepo() {
                    MissingRepo repo = new MissingRepo();
                    repo.doSomething();
                }
            }
            """);

        ExtractionContext.Builder builder = ExtractionContext.builder();
        ExtractionRequest request = new ExtractionRequest(
            testFile, "ServiceTest", "shouldUseMissingRepo", null, null, tempDir
        );

        // When
        extractor.extract(request, builder);
        ExtractionContext context = builder.build();

        // Then - should not throw, just have empty available methods
        assertThat(context.availableMethods()).doesNotContainKey("MissingRepo");
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
