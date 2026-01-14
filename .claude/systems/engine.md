# jwright-engine - Pipeline Orchestration

> **Role**: Default implementation of core contracts and pipeline execution
> **Module**: `jwright-engine`
> **Package**: `ee.jwright.engine.*`
> **Stability**: INTERNAL - Can evolve, must honor stable contracts
> **Critical Path**: Yes - Orchestrates all task execution

## Topology
| Direction | Connected To | Interface | Purpose |
|-----------|--------------|-----------|---------|
| ← Depends | jwright-core | All interfaces | Implements contracts |
| ← Receives | jwright-cli | `JwrightCore` | CLI invocation |
| → Calls | Task implementations | `Task` | Pipeline execution |
| → Calls | ContextExtractor impls | `ContextExtractor` | Context building |
| → Calls | LlmClient impls | `LlmClient` | Code generation |
| → Calls | BuildTool impls | `BuildTool` | Compile and test |

## Quick Health
```bash
# Build engine module
mvn compile -pl jwright-engine

# Run engine tests
mvn test -pl jwright-engine
```

## Key Components
- `DefaultJwrightCore`: Main implementation of `JwrightCore`
- `TaskPipeline`: Executes tasks in order with retry/revert
- `ContextBuilder`: Runs extractors, builds `ExtractionContext`
- `BackupManager`: Snapshot/revert stack for file safety
- `PipelineState`: Mutable runtime state during execution

---
<!-- WARM CONTEXT ENDS ABOVE THIS LINE -->

## Full Documentation

### Package Structure

```
ee.jwright.engine/
├── DefaultJwrightCore.java       # Main entry point implementation
├── pipeline/
│   ├── TaskPipeline.java         # Task orchestration
│   ├── PipelineState.java        # Mutable execution state
│   └── BackupManager.java        # File backup/revert
├── context/
│   └── ContextBuilder.java       # Extractor orchestration
└── template/
    ├── MustacheTemplateEngine.java   # Template implementation
    └── MustacheResolver.java         # Template resolution
```

### TaskPipeline

Orchestrates task execution with safety guarantees.

```java
@Component
public class TaskPipeline {
    private final List<Task> tasks;           // Auto-discovered, ordered
    private final ContextBuilder contextBuilder;
    private final BackupManager backupManager;

    public PipelineResult execute(ImplementRequest request) {
        // 1. Build extraction context
        // 2. Execute tasks in order
        // 3. Handle failures (retry or revert)
        // 4. Commit or rollback
    }
}
```

**Execution Flow:**
1. `ContextBuilder.build()` runs all extractors
2. For each task (sorted by `getOrder()`):
   - Skip if `!shouldRun()`
   - Snapshot current file state
   - Execute with retry if needed
   - On failure: revert (optional) or fail pipeline (required)
3. On success: `BackupManager.commit()`
4. On failure: `BackupManager.revertAll()`

### PipelineState

Mutable state passed through pipeline execution.

```java
public class PipelineState {
    private int attemptNumber;
    private int maxRetries;
    private List<FailedAttempt> failedAttempts;
    private String generatedCode;
    private Task currentTask;
    private TaskStatus lastTaskStatus;

    // Mutators - only TaskPipeline should call
    public void incrementAttempt();
    public void recordFailure(FailedAttempt failure);
    public void setGeneratedCode(String code);

    // Queries - tasks can read
    public boolean canRetry();
    public boolean hasFailures();
    public List<FailedAttempt> getFailedAttempts();
}
```

### BackupManager

Provides rollback capability for all file modifications.

```java
public class BackupManager {
    private final Deque<Snapshot> snapshots = new ArrayDeque<>();

    public void snapshot(Path file);   // Save current state
    public void revertLast();          // Undo last change
    public void revertAll();           // Undo all changes
    public void commit();              // Clear stack, finalize

    record Snapshot(Path file, String content, Instant timestamp) {}
}
```

### ContextBuilder

Runs extractors and builds immutable context.

```java
@Component
public class ContextBuilder {
    private final List<ContextExtractor> extractors;  // Auto-discovered

    public ExtractionContext build(ExtractionRequest request) {
        ExtractionContext.Builder builder = ExtractionContext.builder();

        for (ContextExtractor extractor : extractors) {
            if (extractor.supports(request)) {
                extractor.extract(request, builder);
            }
        }

        return builder.build();  // Immutable result
    }
}
```

### Template Resolution

Templates resolved in order:
1. `.jwright/templates/{name}` - Project-specific
2. `~/.jwright/templates/{name}` - User global
3. Bundled JAR resources - Defaults

## Data Flow Rules

| Component | Mutability | Who Modifies |
|-----------|------------|--------------|
| `ExtractionContext` | Immutable | Built once by `ContextBuilder` |
| `PipelineState` | Mutable | Only `TaskPipeline` |
| `BackupManager` | Mutable | Only `TaskPipeline` |

## Task Failure Handling

| Task Type | On Failure | Pipeline Continues? |
|-----------|------------|---------------------|
| Required (`isRequired()=true`) | Retry up to max, then fail | No |
| Optional (`isRequired()=false`) | Revert changes, mark REVERTED | Yes |

---

**Last Updated:** 2025-01-14
**Status:** Design complete
