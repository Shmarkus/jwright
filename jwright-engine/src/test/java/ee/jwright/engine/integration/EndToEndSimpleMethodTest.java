package ee.jwright.engine.integration;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.LogLevel;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.MethodSignature;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.engine.DefaultJwrightCore;
import ee.jwright.engine.context.ContextBuilder;
import ee.jwright.engine.pipeline.PipelineState;
import ee.jwright.engine.resolve.BuildToolResolver;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for simple method generation.
 * <p>
 * Tests the full pipeline from test to passing code for the happy path:
 * generate an add(a, b) method implementation.
 * </p>
 *
 * <h2>Task 9.1: End-to-end test - simple method</h2>
 */
@DisplayName("9.1 End-to-end test - simple method")
class EndToEndSimpleMethodTest {

    @TempDir
    Path tempDir;

    private Path projectDir;
    private Path testFile;
    private Path implFile;

    @BeforeEach
    void setUp() throws IOException {
        projectDir = tempDir;

        // Create directory structure
        Path srcMainJava = projectDir.resolve("src/main/java");
        Path srcTestJava = projectDir.resolve("src/test/java");
        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcTestJava);

        // Create test file
        testFile = srcTestJava.resolve("CalculatorTest.java");
        Files.writeString(testFile, """
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            class CalculatorTest {
                @Test
                void testAdd() {
                    Calculator calculator = new Calculator();
                    int result = calculator.add(2, 3);
                    assertEquals(5, result);
                }
            }
            """);

        // Create implementation file with empty method
        implFile = srcMainJava.resolve("Calculator.java");
        Files.writeString(implFile, """
            public class Calculator {
                public int add(int a, int b) {
                    // TODO: implement
                    return 0;
                }
            }
            """);
    }

    @Nested
    @DisplayName("Happy path: add(a, b) generation")
    class HappyPathTests {

        @Test
        @DisplayName("full pipeline generates correct add implementation")
        void fullPipelineGeneratesCorrectAddImplementation() throws Exception {
            // Given - a mock LLM that returns correct implementation
            LlmClient mockLlm = createMockLlm(prompt -> {
                // Verify prompt contains expected information
                assertThat(prompt)
                    .contains("add")
                    .contains("int");
                return "```java\nreturn a + b;\n```";
            });

            // Mock build tool that always passes
            BuildTool mockBuildTool = createSuccessfulBuildTool();

            // Real template engine with bundled templates
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );

            // Real code writer
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            // Create ImplementTask with no-arg constructor
            ImplementTask implementTask = new ImplementTask();

            // Use task directly for fine-grained control
            ExtractionContext context = ExtractionContext.builder()
                .testClassName("CalculatorTest")
                .testMethodName("testAdd")
                .testMethodBody("Calculator calculator = new Calculator();\nint result = calculator.add(2, 3);\nassertEquals(5, result);")
                .targetSignature(new MethodSignature("add", "int", List.of("int a", "int b")))
                .build();

            // Create state with all dependencies
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            var result = implementTask.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(state.getGeneratedCode()).isEqualTo("return a + b;");

            // Verify the file was modified
            String modifiedContent = Files.readString(implFile);
            assertThat(modifiedContent).contains("return a + b;");
            assertThat(modifiedContent).doesNotContain("return 0;");
        }

        @Test
        @DisplayName("pipeline writes implementation to correct file")
        void pipelineWritesImplementationToCorrectFile() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt -> "```java\nreturn a + b;\n```");
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createAddMethodContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            implementTask.execute(context, state);

            // Then
            String content = Files.readString(implFile);
            assertThat(content)
                .contains("public class Calculator")
                .contains("public int add(int a, int b)")
                .contains("return a + b;");
        }

        @Test
        @DisplayName("extracts code from markdown code block in LLM response")
        void extractsCodeFromMarkdownCodeBlock() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt -> """
                Here's the implementation:

                ```java
                return a + b;
                ```

                This simply adds the two numbers.
                """);
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createAddMethodContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            implementTask.execute(context, state);

            // Then
            assertThat(state.getGeneratedCode()).isEqualTo("return a + b;");
        }

        @Test
        @DisplayName("template includes test method body in prompt")
        void templateIncludesTestMethodBodyInPrompt() throws Exception {
            // Given
            final StringBuilder capturedPrompt = new StringBuilder();
            LlmClient mockLlm = createMockLlm(prompt -> {
                capturedPrompt.append(prompt);
                return "return a + b;";
            });
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createAddMethodContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When
            implementTask.execute(context, state);

            // Then
            String prompt = capturedPrompt.toString();
            assertThat(prompt)
                .contains("CalculatorTest")
                .contains("testAdd")
                .contains("add(2, 3)")
                .contains("assertEquals(5, result)");
        }
    }

    @Nested
    @DisplayName("Integration with full pipeline")
    class FullPipelineTests {

        @Test
        @DisplayName("DefaultJwrightCore.implement() runs full pipeline")
        void defaultJwrightCoreImplementRunsFullPipeline() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt -> "return a + b;");
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            // Create a context builder that returns a valid context
            ContextBuilder contextBuilder = new ContextBuilder(List.of(createTestContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(implementTask),
                3,
                templateEngine,
                mockLlm,
                codeWriter,
                createBuildToolResolver(mockBuildTool)
            );

            ImplementRequest request = new ImplementRequest(
                projectDir,
                "CalculatorTest#testAdd",
                false,
                LogLevel.INFO
            );

            // When
            PipelineResult result = core.implement(request);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.taskResults()).isNotEmpty();
            assertThat(result.taskResults().get(0).status()).isEqualTo(TaskStatus.SUCCESS);
        }

        @Test
        @DisplayName("pipeline returns final generated code in result")
        void pipelineReturnsFinalGeneratedCodeInResult() throws Exception {
            // Given
            LlmClient mockLlm = createMockLlm(prompt -> "return a + b;");
            BuildTool mockBuildTool = createSuccessfulBuildTool();
            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            // Create a context builder that returns a valid context
            ContextBuilder contextBuilder = new ContextBuilder(List.of(createTestContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(implementTask),
                3,
                templateEngine,
                mockLlm,
                codeWriter,
                createBuildToolResolver(mockBuildTool)
            );

            ImplementRequest request = new ImplementRequest(
                projectDir,
                "CalculatorTest#testAdd",
                false,
                LogLevel.INFO
            );

            // When
            PipelineResult result = core.implement(request);

            // Then
            assertThat(result.finalCode()).isEqualTo("return a + b;");
        }
    }

    private ExtractionContext createAddMethodContext() {
        return ExtractionContext.builder()
            .testClassName("CalculatorTest")
            .testMethodName("testAdd")
            .testMethodBody("Calculator calculator = new Calculator();\nint result = calculator.add(2, 3);\nassertEquals(5, result);")
            .targetSignature(new MethodSignature("add", "int", List.of("int a", "int b")))
            .build();
    }

    /**
     * Creates a test context extractor that returns predefined test context.
     */
    private ContextExtractor createTestContextExtractor() {
        return new ContextExtractor() {
            @Override
            public String getId() {
                return "test-context";
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
                builder.testClassName("CalculatorTest")
                    .testMethodName("testAdd")
                    .testMethodBody("Calculator calculator = new Calculator();\nint result = calculator.add(2, 3);\nassertEquals(5, result);")
                    .targetSignature(new MethodSignature("add", "int", List.of("int a", "int b")));
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

    private BuildToolResolver createBuildToolResolver(BuildTool buildTool) {
        return new BuildToolResolver(List.of(buildTool));
    }
}
