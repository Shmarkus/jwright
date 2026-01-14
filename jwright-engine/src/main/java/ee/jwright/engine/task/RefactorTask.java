package ee.jwright.engine.task;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.build.CompilationResult;
import ee.jwright.core.build.TestResult;
import ee.jwright.core.extract.ExtractionContext;
import ee.jwright.core.extract.MethodSignature;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import ee.jwright.core.task.Task;
import ee.jwright.core.task.TaskResult;
import ee.jwright.core.task.TaskStatus;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.core.write.WriteMode;
import ee.jwright.core.write.WriteRequest;
import ee.jwright.core.write.WriteResult;
import ee.jwright.engine.pipeline.PipelineState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task that refactors generated code to improve quality.
 * <p>
 * This is an optional task that runs after successful implementation.
 * It asks the LLM to improve the code while keeping tests passing.
 * </p>
 *
 * <h2>Order: 200</h2>
 * <p>Runs first in the improvement range (200-299).</p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
@Component
@Order(200)
public class RefactorTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(RefactorTask.class);
    private static final String TEMPLATE_NAME = "refactor.mustache";
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```(?:java)?\\s*\\n([\\s\\S]*?)\\n```",
        Pattern.MULTILINE
    );

    /**
     * Creates a new RefactorTask.
     * <p>
     * Dependencies are obtained from PipelineState during execution.
     * </p>
     */
    public RefactorTask() {
        // Dependencies are obtained from PipelineState during execution
    }

    @Override
    public String getId() {
        return "refactor";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public boolean isRequired() {
        return false;  // Optional task
    }

    @Override
    public boolean shouldRun(ExtractionContext extraction, Object stateObj) {
        PipelineState state = (PipelineState) stateObj;

        // Don't run if previous task failed
        if (state.getLastTaskStatus() != TaskStatus.SUCCESS) {
            log.debug("Skipping refactor: previous task did not succeed");
            return false;
        }

        // Don't run if there's no generated code to refactor
        if (state.getGeneratedCode() == null || state.getGeneratedCode().isEmpty()) {
            log.debug("Skipping refactor: no generated code available");
            return false;
        }

        return true;
    }

    @Override
    public TaskResult execute(ExtractionContext extraction, Object stateObj) {
        PipelineState state = (PipelineState) stateObj;
        int attempt = state.getAttemptNumber();

        // Get dependencies from state
        TemplateEngine templateEngine = state.getTemplateEngine();
        LlmClient llmClient = state.getLlmClient();
        CodeWriter codeWriter = state.getCodeWriter();
        BuildTool buildTool = state.getBuildTool();
        Path projectDir = state.getProjectDir();
        Path implFile = state.getImplFile();

        log.info("Executing RefactorTask");

        try {
            // 1. Build template variables
            Map<String, Object> variables = buildTemplateVariables(extraction, state);

            // 2. Render the prompt template
            String prompt = templateEngine.render(TEMPLATE_NAME, variables);
            log.debug("Generated refactor prompt ({} chars)", prompt.length());

            // 3. Send to LLM
            String response = llmClient.generate(prompt);
            log.debug("Received LLM response ({} chars)", response.length());

            // 4. Extract code from response
            String refactoredCode = extractCode(response);
            log.debug("Extracted refactored code: {}", refactoredCode);

            // 5. Write to file
            WriteRequest writeRequest = new WriteRequest(
                implFile,
                extraction.targetSignature() != null ? extraction.targetSignature().name() : "unknown",
                refactoredCode,
                WriteMode.REPLACE
            );

            if (!codeWriter.supports(writeRequest)) {
                return new TaskResult(getId(), TaskStatus.FAILED, "No code writer supports this file type", attempt);
            }

            WriteResult writeResult = codeWriter.write(writeRequest);
            if (!writeResult.success()) {
                return new TaskResult(getId(), TaskStatus.FAILED, "Failed to write refactored code: " + writeResult.errorMessage(), attempt);
            }

            // 6. Compile
            CompilationResult compileResult = buildTool.compile(projectDir);
            if (!compileResult.success()) {
                log.warn("Refactored code failed to compile");
                return new TaskResult(getId(), TaskStatus.FAILED, "Refactored code failed to compile", attempt);
            }

            // 7. Run test
            TestResult testResult = buildTool.runSingleTest(
                extraction.testClassName(),
                extraction.testMethodName()
            );

            if (!testResult.success()) {
                log.warn("Refactored code broke tests");
                return new TaskResult(getId(), TaskStatus.FAILED, "Refactored code broke tests", attempt);
            }

            // 8. Update state with refactored code
            state.setGeneratedCode(refactoredCode);

            log.info("Refactoring successful!");
            return new TaskResult(getId(), TaskStatus.SUCCESS, "Refactoring complete", attempt);

        } catch (LlmException e) {
            log.error("LLM error during refactoring: {}", e.getMessage(), e);
            return new TaskResult(getId(), TaskStatus.FAILED, "LLM error: " + e.getMessage(), attempt);
        } catch (Exception e) {
            log.error("Unexpected error during refactoring: {}", e.getMessage(), e);
            return new TaskResult(getId(), TaskStatus.FAILED, "Error: " + e.getMessage(), attempt);
        }
    }

    /**
     * Builds template variables for the refactor template.
     * Provides rich context to help the LLM understand what types and methods are available.
     */
    private Map<String, Object> buildTemplateVariables(ExtractionContext extraction, PipelineState state) {
        Map<String, Object> variables = new HashMap<>();

        // The code to refactor
        variables.put("generatedCode", state.getGeneratedCode());

        // Test context
        variables.put("testClassName", extraction.testClassName());
        variables.put("testMethodName", extraction.testMethodName());
        variables.put("testMethodBody", extraction.testMethodBody());

        // Target method signature
        MethodSignature sig = extraction.targetSignature();
        if (sig != null) {
            variables.put("targetMethodName", sig.name());
            variables.put("targetReturnType", sig.returnType());
            variables.put("targetParameters", String.join(", ", sig.parameters()));
        }

        // Type definitions - critical for LLM to know what types/fields are available
        if (extraction.typeDefinitions() != null && !extraction.typeDefinitions().isEmpty()) {
            variables.put("hasTypeDefinitions", true);
            List<Map<String, Object>> types = new ArrayList<>();
            for (var typeDef : extraction.typeDefinitions()) {
                Map<String, Object> t = new HashMap<>();
                t.put("name", typeDef.name());
                if (typeDef.fields() != null && !typeDef.fields().isEmpty()) {
                    List<Map<String, String>> fields = new ArrayList<>();
                    for (var field : typeDef.fields()) {
                        Map<String, String> f = new HashMap<>();
                        String[] parts = field.split("\\s+", 2);
                        if (parts.length == 2) {
                            f.put("type", parts[0]);
                            f.put("name", parts[1]);
                        } else {
                            f.put("type", "");
                            f.put("name", field);
                        }
                        fields.add(f);
                    }
                    t.put("fields", fields);
                }
                if (typeDef.methods() != null && !typeDef.methods().isEmpty()) {
                    List<Map<String, String>> methods = new ArrayList<>();
                    for (var method : typeDef.methods()) {
                        Map<String, String> m = new HashMap<>();
                        m.put("signature", method.name() + "(" + String.join(", ", method.parameters()) + "): " + method.returnType());
                        methods.add(m);
                    }
                    t.put("methods", methods);
                }
                types.add(t);
            }
            variables.put("typeDefinitions", types);
        }

        // Available methods on collaborator types
        if (extraction.availableMethods() != null && !extraction.availableMethods().isEmpty()) {
            variables.put("hasAvailableMethods", true);
            List<Map<String, Object>> methodsByType = new ArrayList<>();
            for (var entry : extraction.availableMethods().entrySet()) {
                Map<String, Object> typeEntry = new HashMap<>();
                typeEntry.put("typeName", entry.getKey());
                List<Map<String, String>> methods = new ArrayList<>();
                for (var method : entry.getValue()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("signature", method.name() + "(" + String.join(", ", method.parameters()) + "): " + method.returnType());
                    methods.add(m);
                }
                typeEntry.put("methods", methods);
                methodsByType.add(typeEntry);
            }
            variables.put("availableMethods", methodsByType);
        }

        // Hints
        if (extraction.hints() != null && !extraction.hints().isEmpty()) {
            variables.put("hasHints", true);
            variables.put("hints", extraction.hints());
        }

        return variables;
    }

    /**
     * Extracts code from LLM response, handling markdown code blocks.
     */
    private String extractCode(String response) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.trim();
    }
}
