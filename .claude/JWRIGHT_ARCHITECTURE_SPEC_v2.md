# jwright Architecture Specification v2

> AI-assisted TDD tool using local language models
> 
> **Core Principle:** "Don't make the model use tools. Make tools wrap the model."

## Table of Contents

1. [Design Philosophy](#design-philosophy)
2. [Stability Tiers](#stability-tiers)
3. [Core Contracts (STABLE)](#core-contracts-stable)
4. [Internal Components](#internal-components)
5. [Configuration](#configuration)
6. [Module Structure](#module-structure)
7. [Extension Guide](#extension-guide)
8. [Implementation Rules](#implementation-rules)

---

## Design Philosophy

### Guiding Principles

1. **Tests are the spec** - If wrong code generated, test wasn't specific enough
2. **Small models are enough** - For atomic TDD, don't need GPT-4
3. **Human stays in control** - No autonomous agents
4. **Privacy by default** - Code never leaves machine (with Ollama)
5. **Refactoring is mandatory** - Not optional, essential for maintainability
6. **Baby steps work** - AI needs same incremental approach as humans

### Architectural Principles

1. **Open-Closed Principle** - Open for extension, closed for modification
2. **Stable Contracts** - Interfaces are frozen once released
3. **Plugin Architecture** - New behavior = new class, not edits
4. **Symmetric Design** - Extractors mirror Writers (language-agnostic core)
5. **Fail Safe** - BackupManager ensures revert is always possible

---

## Stability Tiers

| Tier | Components | Change Policy |
|------|------------|---------------|
| **★ STABLE** | Interfaces, Core Models, API | Frozen. Extensions depend on these. Never change. |
| **INTERNAL** | Default implementations, orchestration | Can evolve, must honor contracts. |
| **EXTENSION** | Plugin JARs (kotlin, gradle, etc.) | Independent lifecycle. |

**Rule:** If you think you need to change a STABLE contract, you're solving the wrong problem.

---

## Core Contracts (STABLE)

### JwrightCore API

The main entry point. CLI and IDE plugins depend on this.

```java
public interface JwrightCore {
    
    InitResult init(Path projectDir) throws JwrightException;
    
    PipelineResult implement(ImplementRequest request) throws JwrightException;
    
    WatchHandle watch(WatchRequest request, WatchCallback callback) throws JwrightException;
}
```

#### Request/Response Models

```java
public record ImplementRequest(
    Path projectDir,
    String target,           // "TestClass#testMethod"
    boolean dryRun,
    LogLevel logLevel
) {}

public record WatchRequest(
    Path projectDir,
    List<Path> watchPaths,
    List<String> ignorePatterns,
    Duration debounce,
    LogLevel logLevel
) {}

public record InitResult(
    Path configFile,
    Path templatesDir
) {}

public record PipelineResult(
    boolean success,
    List<TaskResult> taskResults,
    Path modifiedFile,
    String finalCode
) {
    public boolean hasWarnings() {
        return taskResults.stream()
            .anyMatch(r -> r.status() == TaskStatus.REVERTED);
    }
}

public record TaskResult(
    String taskId,
    TaskStatus status,
    String message,
    int attempts
) {}

public enum TaskStatus { SUCCESS, FAILED, SKIPPED, REVERTED }

public enum LogLevel { QUIET, INFO, DEBUG, TRACE }
```

#### Watch Callback

```java
public interface WatchCallback {
    void onFileChanged(Path file);
    void onTestDetected(String testTarget);
    void onGenerationStarted(String testTarget);
    void onGenerationComplete(PipelineResult result);
    void onError(JwrightException error);
}

public interface WatchHandle {
    boolean isRunning();
    void stop();
    Path getWatchedDirectory();
}
```

---

### Task Interface

Pluggable tasks in the pipeline. Implement to add new pipeline steps.

```java
public interface Task {
    
    String getId();
    
    int getOrder();
    
    boolean isRequired();
    
    boolean shouldRun(ExtractionContext extraction, PipelineState state);
    
    TaskResult execute(ExtractionContext extraction, PipelineState state);
}
```

#### Order Convention

| Range | Purpose |
|-------|---------|
| 100-199 | Generation (implement) |
| 200-299 | Improvement (refactor) |
| 300-399 | Quality (lint, checkstyle) |
| 400-499 | Formatting (spotless, prettier) |
| 500+ | Custom/third-party |

#### Task Properties

| Task | Required | On Fail |
|------|----------|---------|
| ImplementTask | Yes | Retry → Fail |
| RefactorTask | No | Revert → Continue |
| LintTask | No | Revert → Continue |
| FormatTask | No | Revert → Continue |

---

### ContextExtractor Interface

Pluggable extraction. Implement to support new languages or frameworks.

```java
public interface ContextExtractor {
    
    String getId();
    
    int getOrder();
    
    boolean supports(ExtractionRequest request);
    
    void extract(ExtractionRequest request, ExtractionContext.Builder builder);
}
```

#### Extraction Request (Immutable Input)

```java
public record ExtractionRequest(
    Path testFile,
    String testClassName,
    String testMethodName,
    Path implFile,
    String targetMethodName,
    Path sourceRoot
) {}
```

#### Extraction Context (Immutable Output)

```java
public record ExtractionContext(
    // Test info
    String testClassName,
    String testMethodName,
    String testMethodBody,
    
    // Expectations
    List<Assertion> assertions,
    List<MockSetup> mockSetups,
    List<VerifyStatement> verifyStatements,
    
    // User guidance
    List<String> hints,
    
    // Target info
    MethodSignature targetSignature,
    String currentImplementation,
    
    // Type info
    List<TypeDefinition> typeDefinitions,
    Map<String, List<MethodSignature>> availableMethods
) {
    public static Builder builder() { return new Builder(); }
    
    // Builder is mutable, but built result is immutable
    public static class Builder { ... }
}
```

#### Order Convention

| Range | Purpose |
|-------|---------|
| 100-199 | Test structure (method body, class info) |
| 200-299 | Assertions & expectations |
| 300-399 | Mock frameworks |
| 400-499 | Hints & annotations |
| 500-599 | Implementation analysis |
| 600-699 | Type definitions |
| 700-799 | Method signatures |
| 800+ | Custom/third-party |

---

### CodeWriter Interface

Pluggable code writing. Implement to support new languages.

```java
public interface CodeWriter {
    
    String getId();
    
    int getOrder();
    
    boolean supports(WriteRequest request);
    
    WriteResult write(WriteRequest request);
}
```

#### Write Request

```java
public record WriteRequest(
    Path targetFile,
    String targetMethodName,
    String generatedCode,
    WriteMode mode
) {}

public enum WriteMode {
    INJECT,      // Insert method body into existing method
    REPLACE,     // Replace entire method
    APPEND,      // Add new method to class
    CREATE       // Create new file
}

public record WriteResult(
    boolean success,
    String errorMessage
) {
    public static WriteResult success() { return new WriteResult(true, null); }
    public static WriteResult failure(String msg) { return new WriteResult(false, msg); }
}
```

---

### LlmClient Interface

Pluggable LLM providers. Implement to add OpenAI, Claude, etc.

```java
public interface LlmClient {
    
    String getId();
    
    String generate(String prompt) throws LlmException;
    
    boolean isAvailable();
}
```

#### LlmException

```java
public class LlmException extends Exception {
    
    private final ErrorCode code;
    
    public enum ErrorCode {
        TIMEOUT,
        UNAVAILABLE,
        RATE_LIMITED,
        CONTEXT_EXCEEDED,
        INVALID_RESPONSE,
        UNKNOWN
    }
    
    public LlmException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
    
    public LlmException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public ErrorCode getCode() {
        return code;
    }
}
```

---

### BuildTool Interface

Pluggable build systems. Implement to add Gradle, Bazel, etc.

```java
public interface BuildTool {
    
    String getId();
    
    int getOrder();
    
    boolean supports(Path projectDir);
    
    CompilationResult compile(Path projectDir);
    
    TestResult runTests(String testClass);
    
    TestResult runSingleTest(String testClass, String testMethod);
}

public record CompilationResult(
    boolean success,
    List<CompilationError> errors
) {}

public record CompilationError(
    Path file,
    int line,
    String message
) {}

public record TestResult(
    boolean success,
    int passed,
    int failed,
    List<TestFailure> failures
) {}

public record TestFailure(
    String testClass,
    String testMethod,
    String message,
    String stackTrace
) {}
```

---

### TemplateEngine Interface

Pluggable template rendering.

```java
public interface TemplateEngine {
    
    String render(String templateName, Map<String, Object> variables);
    
    boolean templateExists(String templateName);
}
```

#### Template Resolution Order

1. `.jwright/templates/{name}` (project-specific)
2. `~/.jwright/templates/{name}` (user global)
3. Bundled defaults (in JAR resources)

---

### JwrightException

```java
public class JwrightException extends Exception {
    
    private final ErrorCode code;
    
    public enum ErrorCode {
        NO_BUILD_TOOL,
        NO_TEST_FOUND,
        NO_IMPL_FOUND,
        EXTRACTION_FAILED,
        GENERATION_FAILED,
        VALIDATION_FAILED,
        CONFIG_INVALID
    }
    
    public JwrightException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
    
    public ErrorCode getCode() {
        return code;
    }
}
```

---

## Internal Components

These can evolve but must honor stable contracts.

### PipelineState (Mutable Runtime State)

```java
public class PipelineState {
    
    private int attemptNumber;
    private int maxRetries;
    private List<FailedAttempt> failedAttempts;
    private String generatedCode;
    private Task currentTask;
    private TaskStatus lastTaskStatus;
    
    // Mutators
    public void incrementAttempt();
    public void recordFailure(FailedAttempt failure);
    public void setGeneratedCode(String code);
    public void setCurrentTask(Task task);
    public void setLastTaskStatus(TaskStatus status);
    
    // Queries
    public boolean canRetry();
    public boolean hasFailures();
    public int getAttemptNumber();
    public List<FailedAttempt> getFailedAttempts();
}

public record FailedAttempt(
    int attempt,
    String generatedCode,
    String errorMessage,
    CompilationError compilationError,
    TestFailure testFailure
) {}
```

### BackupManager

```java
public class BackupManager {
    
    private final Deque<Snapshot> snapshots = new ArrayDeque<>();
    
    public void snapshot(Path file);
    public void revertLast();
    public void revertAll();
    public void commit();  // Clear stack, changes are permanent
    
    record Snapshot(Path file, String content, Instant timestamp) {}
}
```

### TaskPipeline

```java
@Component
public class TaskPipeline {
    
    private final List<Task> tasks;
    private final ContextBuilder contextBuilder;
    private final BackupManager backupManager;
    
    public TaskPipeline(List<Task> tasks, ContextBuilder contextBuilder, BackupManager backupManager) {
        this.tasks = tasks;
        this.contextBuilder = contextBuilder;
        this.backupManager = backupManager;
    }
    
    public PipelineResult execute(ImplementRequest request) {
        ExtractionContext context = contextBuilder.build(toExtractionRequest(request));
        PipelineState state = new PipelineState(config.getMaxRetries());
        
        List<TaskResult> results = new ArrayList<>();
        
        for (Task task : tasks) {
            if (!task.shouldRun(context, state)) {
                results.add(new TaskResult(task.getId(), TaskStatus.SKIPPED, null, 0));
                continue;
            }
            
            state.setCurrentTask(task);
            backupManager.snapshot(request.implFile());
            
            TaskResult result = executeWithRetry(task, context, state);
            results.add(result);
            
            if (result.status() == TaskStatus.FAILED && task.isRequired()) {
                backupManager.revertAll();
                return new PipelineResult(false, results, null, null);
            }
            
            if (result.status() == TaskStatus.FAILED && !task.isRequired()) {
                backupManager.revertLast();
                results.set(results.size() - 1, 
                    new TaskResult(task.getId(), TaskStatus.REVERTED, result.message(), result.attempts()));
            }
        }
        
        backupManager.commit();
        return new PipelineResult(true, results, request.implFile(), state.getGeneratedCode());
    }
}
```

### ContextBuilder

```java
@Component
public class ContextBuilder {
    
    private final List<ContextExtractor> extractors;
    
    public ContextBuilder(List<ContextExtractor> extractors) {
        this.extractors = extractors;
    }
    
    public ExtractionContext build(ExtractionRequest request) {
        ExtractionContext.Builder builder = ExtractionContext.builder();
        
        for (ContextExtractor extractor : extractors) {
            if (extractor.supports(request)) {
                log.debug("Running extractor: {}", extractor.getId());
                extractor.extract(request, builder);
            }
        }
        
        return builder.build();
    }
}
```

### PromptAnalyzer

```java
@Component
public class PromptAnalyzer {
    
    private static final int DEFAULT_CONTEXT_WARNING = 4096;
    
    public int estimateTokens(String prompt) {
        // Rough heuristic: ~4 chars per token for code
        return prompt.length() / 4;
    }
    
    public void warnIfLarge(String prompt) {
        int estimated = estimateTokens(prompt);
        if (estimated > DEFAULT_CONTEXT_WARNING) {
            log.warn("Prompt is ~{} tokens, exceeds default 4096 context. " +
                     "Ensure your Ollama model is configured with sufficient context.", 
                     estimated);
        }
    }
}
```

---

## Configuration

### Application Configuration

```yaml
# .jwright/config.yaml
jwright:
  llm:
    provider: ollama          # which client to use
    ollama:
      url: http://localhost:11434
      model: qwen2.5-coder:14b
      timeout: 120s
    # claude:                  # unused unless provider: claude
    #   api-key: ${CLAUDE_API_KEY}
    #   model: claude-sonnet-4-20250514
  
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
```

### Provider-Specific Configuration

```java
@ConfigurationProperties(prefix = "jwright.llm.ollama")
public class OllamaConfig {
    private String url = "http://localhost:11434";
    private String model = "qwen2.5-coder:14b";
    private Duration timeout = Duration.ofSeconds(120);
}

@ConfigurationProperties(prefix = "jwright.llm.claude")
public class ClaudeConfig {
    private String apiKey;
    private String model = "claude-sonnet-4-20250514";
    private Duration timeout = Duration.ofSeconds(60);
    private Double temperature = 0.0;
}
```

### Logging Configuration

| CLI Flag | Log Level | Shows |
|----------|-----------|-------|
| (default) | INFO | Progress, results |
| --quiet | WARN | Warnings, errors only |
| --verbose | DEBUG | Prompts, responses, details |
| --trace | TRACE | Everything (dev only) |

---

## Module Structure

```
jwright/
├── jwright-core/               # Interfaces, models, API (★STABLE)
│   └── src/main/java/ee/jwright/core/
│       ├── api/
│       │   ├── JwrightCore.java
│       │   ├── ImplementRequest.java
│       │   ├── WatchRequest.java
│       │   └── ...
│       ├── task/
│       │   ├── Task.java
│       │   ├── TaskResult.java
│       │   └── TaskStatus.java
│       ├── extract/
│       │   ├── ContextExtractor.java
│       │   ├── ExtractionRequest.java
│       │   └── ExtractionContext.java
│       ├── write/
│       │   ├── CodeWriter.java
│       │   ├── WriteRequest.java
│       │   └── WriteMode.java
│       ├── llm/
│       │   ├── LlmClient.java
│       │   └── LlmException.java
│       ├── build/
│       │   ├── BuildTool.java
│       │   ├── CompilationResult.java
│       │   └── TestResult.java
│       ├── template/
│       │   └── TemplateEngine.java
│       └── exception/
│           └── JwrightException.java
│
├── jwright-engine/             # Default implementations (INTERNAL)
│   └── src/main/java/ee/jwright/engine/
│       ├── pipeline/
│       │   ├── TaskPipeline.java
│       │   ├── PipelineState.java
│       │   └── BackupManager.java
│       ├── context/
│       │   └── ContextBuilder.java
│       ├── template/
│       │   ├── MustacheTemplateEngine.java
│       │   └── MustacheResolver.java
│       └── DefaultJwrightCore.java
│
├── jwright-java/               # Java language support (EXTENSION)
│   └── src/main/java/ee/jwright/java/
│       ├── extract/
│       │   ├── JavaTestMethodExtractor.java
│       │   ├── JavaAssertionExtractor.java
│       │   ├── JavaMockitoExtractor.java
│       │   ├── JavaTypeDefinitionExtractor.java
│       │   └── JavaHintExtractor.java
│       ├── write/
│       │   └── JavaMethodBodyWriter.java
│       └── JavaParserUtils.java
│
├── jwright-ollama/             # Ollama LLM support (EXTENSION)
│   └── src/main/java/ee/jwright/ollama/
│       ├── OllamaClient.java
│       └── OllamaConfig.java
│
├── jwright-maven/              # Maven build support (EXTENSION)
│   └── src/main/java/ee/jwright/maven/
│       └── MavenBuildTool.java
│
├── jwright-gradle/             # Gradle build support (EXTENSION)
│   └── src/main/java/ee/jwright/gradle/
│       └── GradleBuildTool.java
│
├── jwright-cli/                # CLI application
│   └── src/main/java/ee/jwright/cli/
│       ├── JwrightCli.java
│       ├── InitCommand.java
│       ├── ImplementCommand.java
│       └── WatchCommand.java
│
└── jwright-bom/                # Bill of Materials for versioning
    └── pom.xml
```

---

## Extension Guide

### Adding a New Task

```java
// jwright-checkstyle/src/.../CheckstyleTask.java

@Component
@Order(350)  // After refactor (200), before format (400)
@ConditionalOnClass(name = "com.puppycrawl.tools.checkstyle.Checker")
public class CheckstyleTask implements Task {
    
    @Override
    public String getId() {
        return "checkstyle";
    }
    
    @Override
    public int getOrder() {
        return 350;
    }
    
    @Override
    public boolean isRequired() {
        return false;  // Revert on failure, don't fail pipeline
    }
    
    @Override
    public boolean shouldRun(ExtractionContext extraction, PipelineState state) {
        return state.getLastTaskStatus() == TaskStatus.SUCCESS;
    }
    
    @Override
    public TaskResult execute(ExtractionContext extraction, PipelineState state) {
        // Run checkstyle, return result
    }
}
```

User adds dependency:

```xml
<dependency>
    <groupId>ee.jwright</groupId>
    <artifactId>jwright-checkstyle</artifactId>
</dependency>
```

Task is automatically discovered and added to pipeline.

### Adding a New Language

```java
// jwright-kotlin/src/.../KotlinTestMethodExtractor.java

@Component
@Order(100)
public class KotlinTestMethodExtractor implements ContextExtractor {
    
    @Override
    public String getId() {
        return "kotlin-test-method";
    }
    
    @Override
    public boolean supports(ExtractionRequest request) {
        return request.testFile().toString().endsWith(".kt");
    }
    
    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        // Use Kotlin compiler API
    }
}

// jwright-kotlin/src/.../KotlinMethodBodyWriter.java

@Component
@Order(100)
public class KotlinMethodBodyWriter implements CodeWriter {
    
    @Override
    public boolean supports(WriteRequest request) {
        return request.targetFile().toString().endsWith(".kt");
    }
    
    // ...
}
```

### Adding a New LLM Provider

```java
// jwright-claude/src/.../ClaudeClient.java

@Component
@ConditionalOnProperty(name = "jwright.llm.provider", havingValue = "claude")
public class ClaudeClient implements LlmClient {
    
    private final ClaudeConfig config;
    
    @Override
    public String getId() {
        return "claude";
    }
    
    @Override
    public String generate(String prompt) throws LlmException {
        // Call Claude API
        // Handle errors, map to LlmException.ErrorCode
    }
    
    @Override
    public boolean isAvailable() {
        return config.getApiKey() != null;
    }
}
```

### Deprecating an Implementation

```java
// v1.0 - Original implementation
@Component
@Order(200)
public class SimpleRefactorTask implements Task { }

// v2.0 - Better implementation, keep old available
@Component
@Order(200)
@ConditionalOnProperty(name = "jwright.refactor.engine", havingValue = "v2", matchIfMissing = true)
public class SmartRefactorTask implements Task { }

@Component
@Order(200)
@ConditionalOnProperty(name = "jwright.refactor.engine", havingValue = "v1")
@Deprecated(since = "2.0", forRemoval = true)
public class SimpleRefactorTask implements Task { }

// v3.0 - Remove deprecated
// Just delete SimpleRefactorTask.java
```

---

## Implementation Rules

### For Contributors

1. **New behavior = new class.** If you're editing an existing class to add features, stop and reconsider.

2. **Never modify STABLE contracts.** If you think you need to, you're solving the wrong problem.

3. **Conditional activation.** Use `@ConditionalOnProperty`, `@ConditionalOnClass` to toggle implementations.

4. **Deprecate, don't delete.** Mark old implementations `@Deprecated`, keep working for 1 major version.

5. **Config switches behavior.** Users control which implementation via `config.yaml`, not code changes.

6. **Tests per implementation.** Each Task/Extractor/Writer has its own tests. Contract compliance tested via interfaces.

7. **Log appropriately.** INFO for user-visible progress, DEBUG for troubleshooting, TRACE for development.

### Data Flow Rules

1. **ExtractionContext is immutable.** Tasks read it, never modify.

2. **PipelineState is mutable.** Only TaskPipeline modifies it, tasks read.

3. **BackupManager owns file safety.** All file writes must allow revert.

4. **Templates are external.** Prompts live in `.mustache` files, not code.

### Extension Rules

1. **Implement stable interfaces.** Never depend on internal components.

2. **Use Spring ordering.** `@Order` annotation controls execution sequence.

3. **Support detection via `supports()`.** Extensions must correctly identify when they apply.

4. **Fail gracefully.** Extensions should catch errors and return appropriate Result objects.

---

## CLI Reference

```bash
# Initialize project
jwright init [--dir <path>]

# Generate implementation for test
jwright implement <TestClass#testMethod> [options]
  --no-refactor     Skip refactoring step
  --dry-run         Show generated code without writing
  --verbose         Show prompts and responses
  --quiet           Minimal output

# Watch for changes
jwright watch [options]
  --verbose         Show details
  --quiet           Minimal output

# Show version
jwright --version

# Show help
jwright --help
```

---

## Appendix: Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Layer                              │
│                   CLI / IDE Plugin / API                         │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  JwrightCore API ★STABLE                         │
│                init() / implement() / watch()                    │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                   TaskPipeline (internal)                        │
│         BackupManager / PipelineState / Orchestration            │
└───────┬─────────┬─────────┬─────────┬─────────┬─────────────────┘
        │         │         │         │         │
        ▼         ▼         ▼         ▼         ▼
┌───────────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐
│  Tasks    │ │Context│ │Template│ │  LLM  │ │ Build │
│ ★STABLE   │ │Extract│ │Engine │ │Client │ │ Tool  │
│           │ │★STABLE│ │★STABLE│ │★STABLE│ │★STABLE│
└───────────┘ └───────┘ └───────┘ └───────┘ └───────┘
      │           │                   │         │
      ▼           ▼                   ▼         ▼
┌───────────┐ ┌───────┐         ┌───────┐ ┌───────┐
│Implement  │ │Java   │         │Ollama │ │Maven  │
│Refactor   │ │Kotlin │         │Claude │ │Gradle │
│Lint...    │ │Groovy │         │OpenAI │ │Bazel  │
└───────────┘ └───────┘         └───────┘ └───────┘
                  │
                  ▼
            ┌───────────┐
            │CodeWriter │
            │ ★STABLE   │
            └───────────┘
```

---

*Document version: 2.0*
*Last updated: 2025-01-14*
*Status: Design complete, ready for implementation*
