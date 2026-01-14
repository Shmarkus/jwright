package ee.jwright.engine;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.InitResult;
import ee.jwright.core.api.JwrightCore;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.api.WatchHandle;
import ee.jwright.core.api.WatchRequest;
import ee.jwright.core.build.BuildTool;
import ee.jwright.core.exception.JwrightException;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.task.Task;
import ee.jwright.core.template.TemplateEngine;
import ee.jwright.core.write.CodeWriter;
import ee.jwright.engine.context.ContextBuilder;
import ee.jwright.engine.pipeline.BackupManager;
import ee.jwright.engine.pipeline.TaskPipeline;
import ee.jwright.engine.resolve.TestTargetResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Default implementation of {@link JwrightCore}.
 * <p>
 * Wires together the ContextBuilder, TaskPipeline, and other components
 * to provide the full jwright functionality.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
@Component
public class DefaultJwrightCore implements JwrightCore {

    private static final Logger log = LoggerFactory.getLogger(DefaultJwrightCore.class);

    private static final String JWRIGHT_DIR = ".jwright";
    private static final String CONFIG_FILE = "config.yaml";
    private static final String TEMPLATES_DIR = "templates";

    private static final String DEFAULT_CONFIG = """
        # jwright configuration
        jwright:
          llm:
            provider: ollama
            ollama:
              url: http://localhost:11434
              model: qwen2.5-coder:14b
              timeout: 120s

          tasks:
            implement:
              timeout: 120s
              max-retries: 5
            refactor:
              enabled: true
              timeout: 60s
              max-retries: 2

          watch:
            paths:
              - src/test/java
            ignore:
              - "**/*.class"
              - "**/target/**"
            debounce: 500ms

          paths:
            source: src/main/java
            test: src/test/java
        """;

    private final ContextBuilder contextBuilder;
    private final List<Task> tasks;
    private final TemplateEngine templateEngine;
    private final LlmClient llmClient;
    private final CodeWriter codeWriter;
    private final BuildTool buildTool;

    @Value("${jwright.tasks.implement.max-retries:5}")
    private int maxRetries = 5;

    /**
     * Creates a new DefaultJwrightCore with the given components.
     *
     * @param contextBuilder the context builder
     * @param tasks          the list of tasks to execute
     * @param templateEngine the template engine (optional, for lazy initialization)
     * @param llmClient      the LLM client (optional, for lazy initialization)
     * @param codeWriter     the code writer (optional, for lazy initialization)
     * @param buildTool      the build tool (optional, for lazy initialization)
     */
    @Autowired
    public DefaultJwrightCore(
            ContextBuilder contextBuilder,
            List<Task> tasks,
            @Autowired(required = false) TemplateEngine templateEngine,
            @Autowired(required = false) LlmClient llmClient,
            @Autowired(required = false) CodeWriter codeWriter,
            @Autowired(required = false) BuildTool buildTool) {
        this.contextBuilder = contextBuilder;
        this.tasks = tasks;
        this.templateEngine = templateEngine;
        this.llmClient = llmClient;
        this.codeWriter = codeWriter;
        this.buildTool = buildTool;
    }

    /**
     * Creates a new DefaultJwrightCore with the given components and max retries.
     * This constructor is primarily for testing.
     *
     * @param contextBuilder the context builder
     * @param tasks          the list of tasks to execute
     * @param maxRetries     maximum retry attempts for required tasks
     */
    public DefaultJwrightCore(ContextBuilder contextBuilder, List<Task> tasks, int maxRetries) {
        this.contextBuilder = contextBuilder;
        this.tasks = tasks;
        this.templateEngine = null;
        this.llmClient = null;
        this.codeWriter = null;
        this.buildTool = null;
        this.maxRetries = maxRetries;
    }

    /**
     * Creates a new DefaultJwrightCore with all dependencies for testing.
     *
     * @param contextBuilder the context builder
     * @param tasks          the list of tasks to execute
     * @param maxRetries     maximum retry attempts for required tasks
     * @param templateEngine the template engine
     * @param llmClient      the LLM client
     * @param codeWriter     the code writer
     * @param buildTool      the build tool
     */
    public DefaultJwrightCore(ContextBuilder contextBuilder, List<Task> tasks, int maxRetries,
                             TemplateEngine templateEngine, LlmClient llmClient,
                             CodeWriter codeWriter, BuildTool buildTool) {
        this.contextBuilder = contextBuilder;
        this.tasks = tasks;
        this.templateEngine = templateEngine;
        this.llmClient = llmClient;
        this.codeWriter = codeWriter;
        this.buildTool = buildTool;
        this.maxRetries = maxRetries;
    }

    @Override
    public InitResult init(Path projectDir) throws JwrightException {
        Path jwrightDir = projectDir.resolve(JWRIGHT_DIR);
        Path configFile = jwrightDir.resolve(CONFIG_FILE);
        Path templatesDir = jwrightDir.resolve(TEMPLATES_DIR);

        try {
            // Create directories
            Files.createDirectories(jwrightDir);
            Files.createDirectories(templatesDir);

            // Write config if it doesn't exist
            if (!Files.exists(configFile)) {
                Files.writeString(configFile, DEFAULT_CONFIG);
                log.info("Created configuration file: {}", configFile);
            } else {
                log.debug("Configuration file already exists: {}", configFile);
            }

            log.info("Initialized jwright in: {}", projectDir);
            return new InitResult(configFile, templatesDir);

        } catch (IOException e) {
            throw new JwrightException(
                JwrightException.ErrorCode.CONFIG_INVALID,
                "Failed to initialize jwright: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public PipelineResult implement(ImplementRequest request) throws JwrightException {
        log.info("Implementing: {}", request.target());

        // Resolve target to get implementation file path
        TestTargetResolver resolver = new TestTargetResolver();
        TestTargetResolver.ResolvedTarget resolved = resolver.resolve(
            request.projectDir(),
            request.target()
        );
        Path implFile = resolved.implFile();

        // Create pipeline with fresh backup manager for this execution
        BackupManager backupManager = new BackupManager();
        TaskPipeline pipeline = new TaskPipeline(
            tasks, contextBuilder, backupManager, maxRetries, implFile,
            request.projectDir(), templateEngine, llmClient, codeWriter, buildTool, resolver
        );

        return pipeline.execute(request);
    }

    @Override
    public WatchHandle watch(WatchRequest request, WatchCallback callback) throws JwrightException {
        // Watch functionality will be implemented in a later phase
        throw new JwrightException(
            JwrightException.ErrorCode.CONFIG_INVALID,
            "Watch functionality not yet implemented"
        );
    }
}
