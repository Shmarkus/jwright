# jwright-core - Stable Contracts

> **Role**: Frozen interfaces that all extensions depend on
> **Module**: `jwright-core`
> **Package**: `ee.jwright.core.*`
> **Stability**: ★STABLE - Never modify these interfaces
> **Critical Path**: Yes - Breaking changes break all extensions

## Topology
| Direction | Connected To | Interface | Purpose |
|-----------|--------------|-----------|---------|
| ← Implements | jwright-engine | `JwrightCore` | Default implementation |
| ← Implements | jwright-java | `ContextExtractor`, `CodeWriter` | Java language support |
| ← Implements | jwright-ollama | `LlmClient` | LLM provider |
| ← Implements | jwright-maven | `BuildTool` | Build system |
| → Used by | jwright-cli | `JwrightCore` | CLI commands |

## Quick Health
```bash
# Compile core module
mvn compile -pl jwright-core

# Run interface contract tests
mvn test -pl jwright-core
```

## Key Interfaces
- `JwrightCore`: Main API - `init()`, `implement()`, `watch()`
- `Task`: Pipeline step contract
- `ContextExtractor`: Test analysis plugin
- `CodeWriter`: Code generation output
- `LlmClient`: LLM provider abstraction
- `BuildTool`: Build system abstraction
- `TemplateEngine`: Prompt template rendering

---
<!-- WARM CONTEXT ENDS ABOVE THIS LINE -->

## Full Documentation

### Package Structure

```
ee.jwright.core/
├── api/
│   ├── JwrightCore.java          # Main entry point
│   ├── ImplementRequest.java     # Request model
│   ├── WatchRequest.java         # Watch mode request
│   ├── PipelineResult.java       # Execution result
│   └── TaskResult.java           # Per-task result
├── task/
│   ├── Task.java                 # Pipeline task interface
│   └── TaskStatus.java           # SUCCESS/FAILED/SKIPPED/REVERTED
├── extract/
│   ├── ContextExtractor.java     # Extraction plugin interface
│   ├── ExtractionRequest.java    # Input to extractors
│   └── ExtractionContext.java    # Immutable extraction output
├── write/
│   ├── CodeWriter.java           # Code writing interface
│   ├── WriteRequest.java         # Write operation input
│   └── WriteMode.java            # INJECT/REPLACE/APPEND/CREATE
├── llm/
│   ├── LlmClient.java            # LLM abstraction
│   └── LlmException.java         # LLM error types
├── build/
│   ├── BuildTool.java            # Build system interface
│   ├── CompilationResult.java    # Compile outcome
│   └── TestResult.java           # Test execution outcome
├── template/
│   └── TemplateEngine.java       # Template rendering
└── exception/
    └── JwrightException.java     # Core exceptions
```

### JwrightCore API

```java
public interface JwrightCore {
    InitResult init(Path projectDir) throws JwrightException;
    PipelineResult implement(ImplementRequest request) throws JwrightException;
    WatchHandle watch(WatchRequest request, WatchCallback callback) throws JwrightException;
}
```

### Task Interface

```java
public interface Task {
    String getId();
    int getOrder();
    boolean isRequired();
    boolean shouldRun(ExtractionContext extraction, PipelineState state);
    TaskResult execute(ExtractionContext extraction, PipelineState state);
}
```

**Order Ranges:**
| Range | Purpose |
|-------|---------|
| 100-199 | Generation (implement) |
| 200-299 | Improvement (refactor) |
| 300-399 | Quality (lint) |
| 400-499 | Formatting |
| 500+ | Custom |

### ContextExtractor Interface

```java
public interface ContextExtractor {
    String getId();
    int getOrder();
    boolean supports(ExtractionRequest request);
    void extract(ExtractionRequest request, ExtractionContext.Builder builder);
}
```

**Order Ranges:**
| Range | Purpose |
|-------|---------|
| 100-199 | Test structure |
| 200-299 | Assertions |
| 300-399 | Mocks |
| 400-499 | Hints |
| 500-599 | Implementation |
| 600-699 | Types |
| 700-799 | Methods |

### CodeWriter Interface

```java
public interface CodeWriter {
    String getId();
    int getOrder();
    boolean supports(WriteRequest request);
    WriteResult write(WriteRequest request);
}
```

### LlmClient Interface

```java
public interface LlmClient {
    String getId();
    String generate(String prompt) throws LlmException;
    boolean isAvailable();
}
```

### BuildTool Interface

```java
public interface BuildTool {
    String getId();
    int getOrder();
    boolean supports(Path projectDir);
    CompilationResult compile(Path projectDir);
    TestResult runTests(String testClass);
    TestResult runSingleTest(String testClass, String testMethod);
}
```

## Extension Rules

1. **Never modify these interfaces** - If you think you need to, you're solving the wrong problem
2. **Implement, don't extend** - Create new implementations, don't subclass
3. **Use `supports()` correctly** - Extensions must accurately detect applicability
4. **Return proper Result objects** - Don't throw exceptions for expected failures

---

**Last Updated:** 2025-01-14
**Status:** Design complete
