package ee.jwright.engine.task;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.extract.*;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.core.write.WriteResult;
import ee.jwright.engine.pipeline.PipelineState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Tests for {@link RefactorTask}.
 */
@ExtendWith(MockitoExtension.class)
class RefactorTaskTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private LlmClient llmClient;

    @Mock
    private CodeWriter codeWriter;

    @Mock
    private BuildTool buildTool;

    private RefactorTask task;
    private PipelineState state;
    private Path projectDir;
    private Path implFile;

    @BeforeEach
    void setUp() {
        projectDir = Path.of("/test/project");
        implFile = projectDir.resolve("src/main/java/Calculator.java");
        task = new RefactorTask();
        state = new PipelineState(3, projectDir, implFile, templateEngine, llmClient, codeWriter, buildTool);
    }

    @Nested
    @DisplayName("7.7 RefactorTask")
    class RefactorTaskTests {

        @Test
        @DisplayName("getId() returns 'refactor'")
        void getIdReturnsRefactor() {
            assertThat(task.getId()).isEqualTo("refactor");
        }

        @Test
        @DisplayName("getOrder() returns 200 (improvement range)")
        void getOrderReturnsImprovementRange() {
            assertThat(task.getOrder()).isGreaterThanOrEqualTo(200);
            assertThat(task.getOrder()).isLessThan(300);
        }

        @Test
        @DisplayName("isRequired() returns false")
        void isRequiredReturnsFalse() {
            assertThat(task.isRequired()).isFalse();
        }

        @Test
        @DisplayName("shouldRun() returns true when previous task succeeded")
        void shouldRunReturnsTrueWhenPreviousSucceeded() {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setLastTaskStatus(TaskStatus.SUCCESS);
            state.setGeneratedCode("return a + b;");

            // When
            boolean shouldRun = task.shouldRun(context, state);

            // Then
            assertThat(shouldRun).isTrue();
        }

        @Test
        @DisplayName("shouldRun() returns false when no generated code")
        void shouldRunReturnsFalseWhenNoCode() {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setLastTaskStatus(TaskStatus.SUCCESS);
            // No generated code set

            // When
            boolean shouldRun = task.shouldRun(context, state);

            // Then
            assertThat(shouldRun).isFalse();
        }

        @Test
        @DisplayName("shouldRun() returns false when previous task failed")
        void shouldRunReturnsFalseWhenPreviousFailed() {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setLastTaskStatus(TaskStatus.FAILED);
            state.setGeneratedCode("return a + b;");

            // When
            boolean shouldRun = task.shouldRun(context, state);

            // Then
            assertThat(shouldRun).isFalse();
        }

        @Test
        @DisplayName("execute() renders refactor template")
        void executeRendersRefactorTemplate() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setGeneratedCode("return a + b;");
            when(templateEngine.render(eq("refactor.mustache"), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            verify(templateEngine).render(eq("refactor.mustache"), any());
        }

        @Test
        @DisplayName("execute() includes generated code in template variables")
        void executeIncludesGeneratedCode() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setGeneratedCode("return a + b;");
            when(templateEngine.render(eq("refactor.mustache"), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(true, 1, 0, List.of()));

            // When
            task.execute(context, state);

            // Then
            verify(templateEngine).render(eq("refactor.mustache"), argThat((Map<String, Object> vars) ->
                "return a + b;".equals(vars.get("generatedCode"))
            ));
        }

        @Test
        @DisplayName("execute() returns SUCCESS when refactored code passes tests")
        void executeReturnsSuccessWhenTestsPass() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setGeneratedCode("int temp = a; return temp + b;");
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a + b;");  // Refactored version
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
        @DisplayName("execute() returns FAILED when refactored code breaks tests")
        void executeReturnsFailedWhenTestsBreak() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setGeneratedCode("return a + b;");
            when(templateEngine.render(anyString(), any())).thenReturn("prompt");
            when(llmClient.generate(anyString())).thenReturn("return a * b;");  // Bad refactor
            when(codeWriter.supports(any())).thenReturn(true);
            when(codeWriter.write(any())).thenReturn(WriteResult.ok());
            when(buildTool.compile(any())).thenReturn(new CompilationResult(true, List.of()));
            when(buildTool.runSingleTest(anyString(), anyString())).thenReturn(new TestResult(false, 0, 1, List.of()));

            // When
            TaskResult result = task.execute(context, state);

            // Then
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        @DisplayName("execute() updates generated code in state on success")
        void executeUpdatesStateOnSuccess() throws LlmException {
            // Given
            ExtractionContext context = createMinimalContext();
            state.setGeneratedCode("int temp = a; return temp + b;");
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
