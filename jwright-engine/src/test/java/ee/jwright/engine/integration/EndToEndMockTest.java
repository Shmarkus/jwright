package ee.jwright.engine.integration;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.LogLevel;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MethodSignature;
import ee.jwright.core.extract.MockSetup;
import ee.jwright.core.extract.VerifyStatement;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.engine.DefaultJwrightCore;
import ee.jwright.engine.context.ContextBuilder;
import ee.jwright.engine.pipeline.PipelineState;
import ee.jwright.engine.task.ImplementTask;
import ee.jwright.engine.template.MustacheResolver;
import ee.jwright.engine.template.MustacheTemplateEngine;
import ee.jwright.java.write.JavaMethodBodyWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for code generation with Mockito mocks.
 * <p>
 * Tests that the pipeline correctly handles mock setups and verify statements
 * when generating code.
 * </p>
 *
 * <h2>Task 9.2: End-to-end test - with mocks</h2>
 */
@DisplayName("9.2 End-to-end test - with mocks")
class EndToEndMockTest {

    @TempDir
    Path tempDir;

    private Path projectDir;
    private Path implFile;

    @BeforeEach
    void setUp() throws IOException {
        projectDir = tempDir;

        // Create directory structure
        Path srcMainJava = projectDir.resolve("src/main/java");
        Path srcTestJava = projectDir.resolve("src/test/java");
        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcTestJava);

        // Create test file with mocks
        Path testFile = srcTestJava.resolve("UserServiceTest.java");
        Files.writeString(testFile, """
            import org.junit.jupiter.api.Test;
            import org.mockito.Mock;
            import static org.mockito.Mockito.*;
            import static org.junit.jupiter.api.Assertions.*;

            class UserServiceTest {
                @Mock
                UserRepository repository;

                @Test
                void testFindUserById() {
                    User expectedUser = new User(1L, "John");
                    when(repository.findById(1L)).thenReturn(expectedUser);

                    UserService service = new UserService(repository);
                    User result = service.findById(1L);

                    assertEquals(expectedUser, result);
                    verify(repository, times(1)).findById(1L);
                }
            }
            """);

        // Create implementation file with empty method
        implFile = srcMainJava.resolve("UserService.java");
        Files.writeString(implFile, """
            public class UserService {
                private final UserRepository repository;

                public UserService(UserRepository repository) {
                    this.repository = repository;
                }

                public User findById(long id) {
                    // TODO: implement
                    return null;
                }
            }
            """);
    }

    @Nested
    @DisplayName("Mock handling in generation")
    class MockHandlingTests {

        @Test
        @DisplayName("prompt includes mock setup information")
        void promptIncludesMockSetupInformation() throws Exception {
            // Given
            final StringBuilder capturedPrompt = new StringBuilder();
            LlmClient mockLlm = createMockLlm(prompt -> {
                capturedPrompt.append(prompt);
                return "return repository.findById(id);";
            });
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createMockContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            implementTask.execute(context, state);

            // Then
            String prompt = capturedPrompt.toString();
            assertThat(prompt)
                .contains("repository")
                .contains("findById")
                .contains("expectedUser");
        }

        @Test
        @DisplayName("prompt includes verify statement information")
        void promptIncludesVerifyStatementInformation() throws Exception {
            // Given
            final StringBuilder capturedPrompt = new StringBuilder();
            LlmClient mockLlm = createMockLlm(prompt -> {
                capturedPrompt.append(prompt);
                return "return repository.findById(id);";
            });
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createMockContextWithVerify();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            implementTask.execute(context, state);

            // Then
            String prompt = capturedPrompt.toString();
            // Verify statements should indicate what methods need to be called
            assertThat(prompt)
                .contains("repository")
                .contains("findById");
        }

        @Test
        @DisplayName("generates code that uses mocked dependencies")
        void generatesCodeThatUsesMockedDependencies() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt ->
                "```java\nreturn repository.findById(id);\n```"
            );
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createMockContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            var result = implementTask.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(state.getGeneratedCode()).isEqualTo("return repository.findById(id);");

            // Verify the file was modified correctly
            String content = Files.readString(implFile);
            assertThat(content).contains("return repository.findById(id);");
        }
    }

    @Nested
    @DisplayName("Full pipeline with mocks")
    class FullPipelineWithMocksTests {

        @Test
        @DisplayName("full pipeline succeeds with mock context")
        void fullPipelineSucceedsWithMockContext() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt -> "return repository.findById(id);");
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ContextBuilder contextBuilder = new ContextBuilder(List.of(createMockContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(implementTask),
                3,
                templateEngine,
                mockLlm,
                codeWriter,
                mockBuildTool
            );

            ImplementRequest request = new ImplementRequest(
                projectDir,
                "UserServiceTest#testFindUserById",
                false,
                LogLevel.INFO
            );

            // When
            PipelineResult result = core.implement(request);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.finalCode()).isEqualTo("return repository.findById(id);");
        }

        @Test
        @DisplayName("pipeline handles multiple mock setups")
        void pipelineHandlesMultipleMockSetups() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt -> {
                // Verify prompt contains multiple mock setups
                assertThat(prompt).contains("repository");
                assertThat(prompt).contains("cache");
                return "User cached = cache.get(id);\nif (cached != null) return cached;\nreturn repository.findById(id);";
            });
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createMultipleMockContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            var result = implementTask.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }
    }

    private ExtractionContext createMockContext() {
        return ExtractionContext.builder()
            .testClassName("UserServiceTest")
            .testMethodName("testFindUserById")
            .testMethodBody("""
                User expectedUser = new User(1L, "John");
                when(repository.findById(1L)).thenReturn(expectedUser);
                UserService service = new UserService(repository);
                User result = service.findById(1L);
                assertEquals(expectedUser, result);
                """)
            .targetSignature(new MethodSignature("findById", "User", List.of("long id")))
            .addMockSetup(new MockSetup("repository", "findById(1L)", "expectedUser"))
            .build();
    }

    private ExtractionContext createMockContextWithVerify() {
        return ExtractionContext.builder()
            .testClassName("UserServiceTest")
            .testMethodName("testFindUserById")
            .testMethodBody("""
                User expectedUser = new User(1L, "John");
                when(repository.findById(1L)).thenReturn(expectedUser);
                UserService service = new UserService(repository);
                User result = service.findById(1L);
                assertEquals(expectedUser, result);
                verify(repository, times(1)).findById(1L);
                """)
            .targetSignature(new MethodSignature("findById", "User", List.of("long id")))
            .addMockSetup(new MockSetup("repository", "findById(1L)", "expectedUser"))
            .addVerifyStatement(new VerifyStatement("repository", "findById(1L)", "1"))
            .build();
    }

    private ExtractionContext createMultipleMockContext() {
        return ExtractionContext.builder()
            .testClassName("UserServiceTest")
            .testMethodName("testFindUserByIdWithCache")
            .testMethodBody("""
                User expectedUser = new User(1L, "John");
                when(cache.get(1L)).thenReturn(null);
                when(repository.findById(1L)).thenReturn(expectedUser);
                UserService service = new UserService(repository, cache);
                User result = service.findById(1L);
                assertEquals(expectedUser, result);
                """)
            .targetSignature(new MethodSignature("findById", "User", List.of("long id")))
            .addMockSetup(new MockSetup("cache", "get(1L)", "null"))
            .addMockSetup(new MockSetup("repository", "findById(1L)", "expectedUser"))
            .build();
    }

    /**
     * Creates a context extractor for test with mocks.
     */
    private ContextExtractor createMockContextExtractor() {
        return new ContextExtractor() {
            @Override
            public String getId() {
                return "mock-context";
            }

            @Override
            public int getOrder() {
                return 100;
            }

            @Override
            public boolean supports(ExtractionRequest request) {
                return true;
            }

            @Override
            public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
                builder.testClassName("UserServiceTest")
                    .testMethodName("testFindUserById")
                    .testMethodBody("""
                        User expectedUser = new User(1L, "John");
                        when(repository.findById(1L)).thenReturn(expectedUser);
                        UserService service = new UserService(repository);
                        User result = service.findById(1L);
                        assertEquals(expectedUser, result);
                        """)
                    .targetSignature(new MethodSignature("findById", "User", List.of("long id")))
                    .addMockSetup(new MockSetup("repository", "findById(1L)", "expectedUser"));
            }
        };
    }

    /**
     * Functional interface for LLM generation.
     */
    @FunctionalInterface
    interface LlmGenerator {
        String generate(String prompt);
    }

    private LlmClient createMockLlm(LlmGenerator generator) {
        return new LlmClient() {
            @Override
            public String getId() {
                return "mock";
            }

            @Override
            public String generate(String prompt) {
                return generator.generate(prompt);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    private BuildTool createSuccessfulBuildTool() {
        return new BuildTool() {
            @Override
            public String getId() {
                return "mock";
            }

            @Override
            public int getOrder() {
                return 100;
            }

            @Override
            public boolean supports(Path projectDir) {
                return true;
            }

            @Override
            public CompilationResult compile(Path projectDir) {
                return new CompilationResult(true, List.of());
            }

            @Override
            public TestResult runTests(String testClass) {
                return new TestResult(true, 1, 0, List.of());
            }

            @Override
            public TestResult runSingleTest(String testClass, String testMethod) {
                return new TestResult(true, 1, 0, List.of());
            }
        };
    }
}
