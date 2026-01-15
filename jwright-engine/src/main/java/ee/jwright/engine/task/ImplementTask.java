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
 * Task that generates implementation code using an LLM.
 * <p>
 * This is the core task of jwright. It:
 * <ol>
 *   <li>Renders the implement.mustache template with extraction context</li>
 *   <li>Sends the prompt to the LLM</li>
 *   <li>Extracts the code from the response</li>
 *   <li>Writes the code to the target file</li>
 *   <li>Compiles and runs tests to validate</li>
 * </ol>
 *
 * <h2>Order: 100</h2>
 * <p>Runs first in the generation range (100-199).</p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
@Component
@Order(100)
public class ImplementTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ImplementTask.class);
    private static final String TEMPLATE_NAME = "implement.mustache";
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```(?:java)?\\s*\\n([\\s\\S]*?)\\n```",
        Pattern.MULTILINE
    );

    /**
     * Creates a new ImplementTask.
     * <p>
     * Dependencies are obtained from PipelineState during execution.
     * </p>
     */
    public ImplementTask() {
        // Dependencies are obtained from PipelineState during execution
    }

    @Override
    public String getId() {
        return "implement";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean shouldRun(ExtractionContext extraction, Object state) {
        // Always run if we have valid context
        return extraction != null && extraction.testClassName() != null;
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

        log.info("Executing ImplementTask (attempt {})", attempt);

        try {
            // 1. Build template variables from extraction context
            Map<String, Object> variables = buildTemplateVariables(extraction, state);

            // 2. Render the prompt template
            String prompt = templateEngine.render(TEMPLATE_NAME, variables);
            log.debug("Generated prompt ({} chars)", prompt.length());

            // 3. Send to LLM
            String response = llmClient.generate(prompt);
            log.debug("Received LLM response ({} chars)", response.length());

            // 4. Extract code from response
            String generatedCode = extractCode(response);
            log.debug("Extracted code: {}", generatedCode);

            // 5. Store in state
            state.setGeneratedCode(generatedCode);

            // If dry-run, skip file write and validation
            if (state.isDryRun()) {
                log.info("Dry-run mode: skipping file write and validation");
                return new TaskResult(getId(), TaskStatus.SUCCESS, "Code generated (dry-run)", attempt);
            }

            // 6. Write to file
            WriteRequest writeRequest = new WriteRequest(
                implFile,
                extraction.targetSignature() != null ? extraction.targetSignature().name() : "unknown",
                generatedCode,
                WriteMode.INJECT
            );

            if (!codeWriter.supports(writeRequest)) {
                return new TaskResult(getId(), TaskStatus.FAILED, "No code writer supports this file type", attempt);
            }

            WriteResult writeResult = codeWriter.write(writeRequest);
            if (!writeResult.success()) {
                return new TaskResult(getId(), TaskStatus.FAILED, "Failed to write code: " + writeResult.errorMessage(), attempt);
            }

            // 7. Compile
            CompilationResult compileResult = buildTool.compile(projectDir);
            if (!compileResult.success()) {
                String errorMsg = formatCompilationErrors(compileResult);
                log.warn("Compilation failed: {}", errorMsg);
                return new TaskResult(getId(), TaskStatus.FAILED, "Compilation failed: " + errorMsg, attempt);
            }

            // 8. Run test
            TestResult testResult = buildTool.runSingleTest(
                extraction.testClassName(),
                extraction.testMethodName()
            );

            if (!testResult.success()) {
                String errorMsg = formatTestFailures(testResult);
                log.warn("Test failed: {}", errorMsg);
                return new TaskResult(getId(), TaskStatus.FAILED, "Test failed: " + errorMsg, attempt);
            }

            log.info("Implementation successful!");
            return new TaskResult(getId(), TaskStatus.SUCCESS, "Implementation complete", attempt);

        } catch (LlmException e) {
            log.error("LLM error: {}", e.getMessage(), e);
            return new TaskResult(getId(), TaskStatus.FAILED, "LLM error: " + e.getMessage(), state.getAttemptNumber());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return new TaskResult(getId(), TaskStatus.FAILED, "Error: " + e.getMessage(), state.getAttemptNumber());
        }
    }

    /**
     * Builds template variables from the extraction context.
     */
    private Map<String, Object> buildTemplateVariables(ExtractionContext extraction, PipelineState state) {
        Map<String, Object> variables = new HashMap<>();

        // Basic test information
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

        // Assertions
        if (extraction.assertions() != null && !extraction.assertions().isEmpty()) {
            variables.put("hasAssertions", true);
            List<Map<String, Object>> assertions = new ArrayList<>();
            for (var assertion : extraction.assertions()) {
                Map<String, Object> a = new HashMap<>();
                a.put("type", assertion.type());
                a.put("expected", assertion.expected());
                a.put("actual", assertion.actual());
                assertions.add(a);
            }
            variables.put("assertions", assertions);
        }

        // Mock setups
        if (extraction.mockSetups() != null && !extraction.mockSetups().isEmpty()) {
            variables.put("hasMockSetups", true);
            List<Map<String, Object>> mocks = new ArrayList<>();
            for (var mock : extraction.mockSetups()) {
                Map<String, Object> m = new HashMap<>();
                m.put("mockObject", mock.mockObject());
                m.put("methodCall", mock.method());
                m.put("returnValue", mock.returnValue());
                mocks.add(m);
            }
            variables.put("mockSetups", mocks);
        }

        // Verify statements
        if (extraction.verifyStatements() != null && !extraction.verifyStatements().isEmpty()) {
            variables.put("hasVerifyStatements", true);
            List<Map<String, Object>> verifies = new ArrayList<>();
            for (var v : extraction.verifyStatements()) {
                Map<String, Object> verify = new HashMap<>();
                verify.put("mockObject", v.mockObject());
                verify.put("methodCall", v.method());
                verify.put("times", v.times());
                verifies.add(verify);
            }
            variables.put("verifyStatements", verifies);
        }

        // Hints
        if (extraction.hints() != null && !extraction.hints().isEmpty()) {
            variables.put("hasHints", true);
            variables.put("hints", extraction.hints());
        }

        // Current implementation
        if (extraction.currentImplementation() != null && !extraction.currentImplementation().isEmpty()) {
            variables.put("hasCurrentImplementation", true);
            variables.put("currentImplementation", extraction.currentImplementation());
        }

        // Type definitions
        if (extraction.typeDefinitions() != null && !extraction.typeDefinitions().isEmpty()) {
            variables.put("hasTypeDefinitions", true);
            List<Map<String, Object>> types = new ArrayList<>();
            for (var typeDef : extraction.typeDefinitions()) {
                Map<String, Object> t = new HashMap<>();
                t.put("name", typeDef.name());
                if (typeDef.fields() != null) {
                    // fields is List<String>, each containing "type name" format
                    List<Map<String, String>> fields = new ArrayList<>();
                    for (var field : typeDef.fields()) {
                        Map<String, String> f = new HashMap<>();
                        // Parse "type name" format
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
                types.add(t);
            }
            variables.put("typeDefinitions", types);
        }

        // Available methods
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

        // Failed attempts (for retry prompts)
        if (state.hasFailures()) {
            variables.put("hasFailedAttempts", true);
            List<Map<String, Object>> failures = new ArrayList<>();
            for (var failure : state.getFailedAttempts()) {
                Map<String, Object> f = new HashMap<>();
                f.put("attemptNumber", failure.attempt());
                f.put("generatedCode", failure.generatedCode());
                f.put("errorMessage", failure.errorMessage());
                if (failure.compilationError() != null) {
                    List<Map<String, Object>> compErrors = new ArrayList<>();
                    Map<String, Object> e = new HashMap<>();
                    e.put("line", failure.compilationError().line());
                    e.put("message", failure.compilationError().message());
                    compErrors.add(e);
                    f.put("compilationErrors", compErrors);
                }
                if (failure.testFailure() != null) {
                    List<Map<String, Object>> testFails = new ArrayList<>();
                    Map<String, Object> t = new HashMap<>();
                    t.put("testName", failure.testFailure().testMethod());
                    t.put("message", failure.testFailure().message());
                    testFails.add(t);
                    f.put("testFailures", testFails);
                }
                failures.add(f);
            }
            variables.put("failedAttempts", failures);
        }

        return variables;
    }

    /**
     * Extracts code from LLM response, handling markdown code blocks.
     */
    private String extractCode(String response) {
        // Try to extract from markdown code block
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // If no code block, return trimmed response
        return response.trim();
    }

    /**
     * Formats compilation errors for error message.
     */
    private String formatCompilationErrors(CompilationResult result) {
        if (result.errors() == null || result.errors().isEmpty()) {
            return "Unknown compilation error";
        }

        StringBuilder sb = new StringBuilder();
        for (var error : result.errors()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(error.file()).append(":").append(error.line()).append(": ").append(error.message());
        }
        return sb.toString();
    }

    /**
     * Formats test failures for error message.
     */
    private String formatTestFailures(TestResult result) {
        if (result.failures() == null || result.failures().isEmpty()) {
            return "Unknown test failure";
        }

        StringBuilder sb = new StringBuilder();
        for (var failure : result.failures()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(failure.testMethod()).append(": ").append(failure.message());
        }
        return sb.toString();
    }
}
