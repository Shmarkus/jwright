package ee.jwright.engine.integration;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.LogLevel;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.extract.ContextExtractor;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.ExtractionRequest;
import ee.jwright.core.extract.MethodSignature;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for retry scenarios.
 * <p>
 * Tests that when the first implementation attempt fails (compilation error
 * or test failure), the pipeline retries with error feedback and eventually
 * produces working code.
 * </p>
 *
 * <h2>Task 9.3: End-to-end test - retry scenario</h2>
 */
@DisplayName("9.3 End-to-end test - retry scenario")
class EndToEndRetryTest {

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

        // Create test file
        Path testFile = srcTestJava.resolve("CalculatorTest.java");
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
    @DisplayName("Retry on compilation failure")
    class RetryOnCompilationFailureTests {

        @Test
        @DisplayName("retries when first attempt produces invalid code")
        void retriesWhenFirstAttemptProducesInvalidCode() throws Exception {
            // Given - LLM that fails first time, succeeds second time
            AtomicInteger attemptCount = new AtomicInteger(0);
            LlmClient mockLlm = createMockLlm(prompt -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt == 1) {
                    return "return a + b // missing semicolon";
                }
                return "return a + b;";
            });

            // Build tool that fails on first attempt, succeeds on second
            AtomicInteger compileCount = new AtomicInteger(0);
            BuildTool mockBuildTool = new BuildTool() {
                @Override
                public String getId() { return "mock"; }

                @Override
                public int getOrder() { return 100; }

                @Override
                public boolean supports(Path projectDir) { return true; }

                @Override
                public CompilationResult compile(Path projectDir) {
                    int attempt = compileCount.incrementAndGet();
                    if (attempt == 1) {
                        return new CompilationResult(false, List.of(
                            new CompilationError(Path.of("Calculator.java"), 3, "';' expected")
                        ));
                    }
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

            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ContextBuilder contextBuilder = new ContextBuilder(List.of(createTestContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(new ImplementTask()),
                3,
                templateEngine,
                mockLlm,
                codeWriter,
                mockBuildTool
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
            assertThat(attemptCount.get()).isGreaterThanOrEqualTo(2); // At least two attempts needed
            assertThat(result.finalCode()).isEqualTo("return a + b;");
        }

        @Test
        @DisplayName("includes previous error in retry prompt")
        void includesPreviousErrorInRetryPrompt() throws Exception {
            // Given
            AtomicInteger attemptCount = new AtomicInteger(0);
            StringBuilder secondPrompt = new StringBuilder();

            LlmClient mockLlm = createMockLlm(prompt -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt == 1) {
                    return "return wrong;";
                }
                secondPrompt.append(prompt);
                return "return a + b;";
            });

            AtomicInteger compileCount = new AtomicInteger(0);
            BuildTool mockBuildTool = new BuildTool() {
                @Override
                public String getId() { return "mock"; }

                @Override
                public int getOrder() { return 100; }

                @Override
                public boolean supports(Path projectDir) { return true; }

                @Override
                public CompilationResult compile(Path projectDir) {
                    int attempt = compileCount.incrementAndGet();
                    if (attempt == 1) {
                        return new CompilationResult(false, List.of(
                            new CompilationError(Path.of("Calculator.java"), 3, "cannot find symbol: wrong")
                        ));
                    }
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

            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ImplementTask implementTask = new ImplementTask();

            ExtractionContext context = createAddMethodContext();
            PipelineState state = new PipelineState(3, projectDir, implFile, templateEngine, mockLlm, codeWriter, mockBuildTool);

            // When - manually simulate pipeline retry logic
            var result1 = implementTask.execute(context, state);
            assertThat(result1.status()).isEqualTo(TaskStatus.FAILED);

            // Record failure and increment attempt
            state.recordFailure(new ee.jwright.engine.pipeline.FailedAttempt(
                1, "return wrong;", result1.message(), null, null
            ));
            state.incrementAttempt();

            // Second attempt should include error context
            var result2 = implementTask.execute(context, state);

            // Then
            assertThat(result2.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(secondPrompt.toString()).contains("Failed Attempt");
            assertThat(secondPrompt.toString()).contains("return wrong;");
        }
    }

    @Nested
    @DisplayName("Retry on test failure")
    class RetryOnTestFailureTests {

        @Test
        @DisplayName("retries when test fails")
        void retriesWhenTestFails() throws Exception {
            // Given - LLM returns wrong value first, correct second
            AtomicInteger attemptCount = new AtomicInteger(0);
            LlmClient mockLlm = createMockLlm(prompt -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt == 1) {
                    return "return 0;"; // Wrong implementation
                }
                return "return a + b;";
            });

            // Build tool that always compiles, but first test fails
            AtomicInteger testCount = new AtomicInteger(0);
            BuildTool mockBuildTool = new BuildTool() {
                @Override
                public String getId() { return "mock"; }

                @Override
                public int getOrder() { return 100; }

                @Override
                public boolean supports(Path projectDir) { return true; }

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
                    int attempt = testCount.incrementAndGet();
                    if (attempt == 1) {
                        return new TestResult(false, 0, 1, List.of(
                            new TestFailure("CalculatorTest", "testAdd", "expected: <5> but was: <0>", null)
                        ));
                    }
                    return new TestResult(true, 1, 0, List.of());
                }
            };

            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ContextBuilder contextBuilder = new ContextBuilder(List.of(createTestContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(new ImplementTask()),
                3,
                templateEngine,
                mockLlm,
                codeWriter,
                mockBuildTool
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
            assertThat(attemptCount.get()).isGreaterThanOrEqualTo(2); // At least two attempts
            assertThat(result.finalCode()).isEqualTo("return a + b;");
        }

        @Test
        @DisplayName("succeeds on third attempt after two failures")
        void succeedsOnThirdAttemptAfterTwoFailures() throws Exception {
            // Given
            AtomicInteger attemptCount = new AtomicInteger(0);
            LlmClient mockLlm = createMockLlm(prompt -> {
                int attempt = attemptCount.incrementAndGet();
                return switch (attempt) {
                    case 1 -> "return 0;";
                    case 2 -> "return 1;";
                    default -> "return a + b;";
                };
            });

            AtomicInteger testCount = new AtomicInteger(0);
            BuildTool mockBuildTool = new BuildTool() {
                @Override
                public String getId() { return "mock"; }

                @Override
                public int getOrder() { return 100; }

                @Override
                public boolean supports(Path projectDir) { return true; }

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
                    int attempt = testCount.incrementAndGet();
                    if (attempt <= 2) {
                        return new TestResult(false, 0, 1, List.of(
                            new TestFailure("CalculatorTest", "testAdd", "expected: <5> but was: <" + (attempt - 1) + ">", null)
                        ));
                    }
                    return new TestResult(true, 1, 0, List.of());
                }
            };

            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ContextBuilder contextBuilder = new ContextBuilder(List.of(createTestContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(new ImplementTask()),
                5, // Allow up to 5 retries
                templateEngine,
                mockLlm,
                codeWriter,
                mockBuildTool
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
            assertThat(attemptCount.get()).isGreaterThanOrEqualTo(3); // At least three attempts
        }
    }

    @Nested
    @DisplayName("Max retries exceeded")
    class MaxRetriesExceededTests {

        @Test
        @DisplayName("fails when max retries exceeded")
        void failsWhenMaxRetriesExceeded() throws Exception {
            // Given - LLM always returns wrong code
            LlmClient mockLlm = createMockLlm(prompt -> "return 0;");

            BuildTool mockBuildTool = new BuildTool() {
                @Override
                public String getId() { return "mock"; }

                @Override
                public int getOrder() { return 100; }

                @Override
                public boolean supports(Path projectDir) { return true; }

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
                    return new TestResult(false, 0, 1, List.of(
                        new TestFailure("CalculatorTest", "testAdd", "expected: <5> but was: <0>", null)
                    ));
                }
            };

            TemplateEngine templateEngine = new MustacheTemplateEngine(
                new MustacheResolver(projectDir, null)
            );
            CodeWriter codeWriter = new JavaMethodBodyWriter();

            ContextBuilder contextBuilder = new ContextBuilder(List.of(createTestContextExtractor()));
            DefaultJwrightCore core = new DefaultJwrightCore(
                contextBuilder,
                List.of(new ImplementTask()),
                2, // Only 2 retries allowed
                templateEngine,
                mockLlm,
                codeWriter,
                mockBuildTool
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
            assertThat(result.success()).isFalse();
            assertThat(result.taskResults()).isNotEmpty();
            assertThat(result.taskResults().get(0).status()).isEqualTo(TaskStatus.FAILED);
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
}
