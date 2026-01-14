package ee.jwright.engine.task;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestFailure;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.extract.*;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.core.write.WriteRequest;
import ee.jwright.core.write.WriteResult;
import ee.jwright.engine.pipeline.PipelineState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ImplementTask}.
 */
@ExtendWith(MockitoExtension.class)
class ImplementTaskTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private LlmClient llmClient;

    @Mock
    private CodeWriter codeWriter;

    @Mock
    private BuildTool buildTool;

    private ImplementTask task;
    private PipelineState state;
    private Path projectDir;
    private Path implFile;

    @BeforeEach
    void setUp() {
        projectDir = Path.of("/test/project");
        implFile = projectDir.resolve("src/main/java/Calculator.java");
        task = new ImplementTask();
        state = new PipelineState(3, projectDir, implFile, templateEngine, llmClient, codeWriter, buildTool);
    }

    @Nested
    @DisplayName("7.2 basic generation")
    class BasicGenerationTests {

        @Test
        @DisplayName("getId() returns 'implement'")
        void getIdReturnsImplement() {
            assertThat(task.getId()).isEqualTo("implement");
        }

        @Test
        @DisplayName("getOrder() returns 100 (generation range)")
        void getOrderReturnsGenerationRange() {
            assertThat(task.getOrder()).isGreaterThanOrEqualTo(100);
            assertThat(task.getOrder()).isLessThan(200);
        }

        @Test
        @DisplayName("isRequired() returns true")
        void isRequiredReturnsTrue() {
            assertThat(task.isRequired()).isTrue();
        }

        @Test
        @DisplayName("shouldRun() returns true for valid context")
        void shouldRunReturnsTrueForValidContext() {
            // Given
            ExtractionContext context = createMinimalContext();

            // When
            boolean shouldRun = task.shouldRun(context, state);

            // Then
            assertThat(shouldRun).isTrue();
        }

        @Test
        @DisplayName("execute() renders template with extraction context")
        void executeRendersTemplateWithContext() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(eq("implement.mustache"), any())).thenReturn("rendered prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(templateEngine).render(eq("implement.mustache"), captor.capture());

            Map<String, Object> variables = captor.getValue();
            assertThat(variables).containsKey("testClassName");
            assertThat(variables).containsKey("testMethodName");
            assertThat(variables).containsKey("testMethodBody");
        }

        @Test
        @DisplayName("execute() sends rendered prompt to LLM")
        void executeSendsPromptToLlm() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("implement the add method");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            verify(llmClient).generate("implement the add method");
        }

        @Test
        @DisplayName("execute() writes generated code via CodeWriter")
        void executeWritesGeneratedCode() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            ArgumentCaptor<WriteRequest> captor = ArgumentCaptor.forClass(WriteRequest.class);
            verify(codeWriter).write(captor.capture());

            WriteRequest request = captor.getValue();
            assertThat(request.generatedCode()).isEqualTo("return a + b;");
        }

        @Test
        @DisplayName("execute() returns SUCCESS when code compiles and test passes")
        void executeReturnsSuccessWhenValid() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            TaskResult result = task.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }

        @Test
        @DisplayName("execute() stores generated code in state")
        void executeStoresGeneratedCodeInState() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            assertThat(state.getGeneratedCode()).isEqualTo("return a + b;");
        }

        @Test
        @DisplayName("execute() extracts code from markdown code block")
        void executeExtractsCodeFromMarkdown() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("```java\nreturn a + b;\n```");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            ArgumentCaptor<WriteRequest> captor = ArgumentCaptor.forClass(WriteRequest.class);
            verify(codeWriter).write(captor.capture());
            assertThat(captor.getValue().generatedCode()).isEqualTo("return a + b;");
        }
    }

    @Nested
    @DisplayName("7.3 compile validation")
    class CompileValidationTests {

        @Test
        @DisplayName("execute() compiles code after writing")
        void executeCompilesAfterWriting() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            verify(buildTool).compile(any());
        }

        @Test
        @DisplayName("execute() returns FAILED when compilation fails")
        void executeReturnsFailedWhenCompilationFails() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("invalid code {{{");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(false,
                List.of(new CompilationError(Path.of("file.java"), 1, "syntax error"))
            ));

            // When
            TaskResult result = task.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.message()).containsIgnoringCase("compilation");
        }
    }

    @Nested
    @DisplayName("7.4 test validation")
    class TestValidationTests {

        @Test
        @DisplayName("execute() runs test after successful compilation")
        void executeRunsTestAfterCompilation() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            verify(buildTool).runSingleTest("CalculatorTest", "testAdd");
        }

        @Test
        @DisplayName("execute() returns FAILED when test fails")
        void executeReturnsFailedWhenTestFails() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return 0;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(false, 0, 1,
                List.of(new TestFailure("CalculatorTest", "testAdd", "expected: 5 but was: 0", null))
            ));

            // When
            TaskResult result = task.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.message()).containsIgnoringCase("test");
        }

        @Test
        @DisplayName("execute() skips test run when compilation fails")
        void executeSkipsTestWhenCompilationFails() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("invalid");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(false, List.of()));

            // When
            task.execute(context, state);

            // Then
            verify(buildTool, never()).runSingleTest(anyString(), anyString());
        }
    }

    private ExtractionContext createMinimalContext() {
        return ExtractionContext.builder()
            .testClassName("CalculatorTest")
            .testMethodName("testAdd")
            .testMethodBody("assertEquals(5, calculator.add(2, 3));")
            .targetSignature(new MethodSignature("add", "int", List.of("int a", "int b")))
            .build();
    }
}
