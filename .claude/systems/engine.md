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
- `FileWatcherService`: Monitors file changes and triggers implementation
- `TestChangeHandler`: Coordinates watch-to-implement workflow

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
├── template/
│   ├── MustacheTemplateEngine.java   # Template implementation
│   └── MustacheResolver.java         # Template resolution
└── watch/
    ├── FileWatcherService.java       # File system watch implementation
    ├── DefaultWatchHandle.java       # Watch handle lifecycle
    ├── WatchSession.java             # Active watch session state
    ├── DebounceHandler.java          # File change debouncing
    ├── TestFileDetector.java         # Identifies test file changes
    ├── FailingTestFinder.java        # Locates failing tests
    └── TestChangeHandler.java        # Orchestrates test->implement
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

## Watch Mode Architecture

Watch mode provides continuous TDD by monitoring test files and automatically implementing new/modified tests.

### FileWatcherService

Core service implementing file system watching.

```java
@Service
public class FileWatcherService {
    public WatchHandle watch(WatchRequest request, WatchCallback callback);
    // Uses Java NIO WatchService for efficient file monitoring
}
```

**Responsibilities:**
- Monitor configured paths for file changes
- Filter events based on ignore patterns
- Delegate to appropriate handlers

### WatchSession

Encapsulates the state of an active watch session.

```java
public class WatchSession {
    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeys;
    private final AtomicBoolean running;

    public void start();
    public void stop();
    public boolean isRunning();
}
```

**Lifecycle:**
1. Created when watch starts
2. Registers watch keys for configured paths
3. Polls for file events until stopped
4. Cleans up resources on stop

### DebounceHandler

Prevents redundant processing when files change rapidly.

```java
public class DebounceHandler {
    public void schedule(Path file, Runnable action);
    // Delays action execution until changes settle
}
```

**Behavior:**
- Default debounce: 500ms (configurable)
- Resets timer on each change to same file
- Executes action only after quiet period

### TestFileDetector

Identifies whether a changed file is a test file.

```java
public class TestFileDetector {
    public boolean isTestFile(Path file);
    // Checks: ends with "Test.java", in test source path
}
```

### FailingTestFinder

Locates failing test methods in a test class.

```java
public class FailingTestFinder {
    public List<String> findFailingTests(Path testFile);
    // Runs tests, parses results, returns TestClass#method targets
}
```

**Strategy:**
1. Execute test class using BuildTool
2. Parse test results
3. Identify failed/errored tests
4. Return fully qualified test targets

### TestChangeHandler

Orchestrates the watch-to-implement workflow.

```java
public class TestChangeHandler {
    public void handleTestChange(Path testFile, WatchCallback callback);
}
```

**Workflow:**
1. Verify file is a test file
2. Find failing tests in the file
3. For each failing test:
   - Notify callback of test detected
   - Create ImplementRequest
   - Execute pipeline
   - Notify callback of result
4. Handle errors gracefully

### Watch Flow

```
File Change → Debounce → Test Detection → Find Failing Tests → Implement Each → Callback
     ↓           ↓              ↓                  ↓                  ↓            ↓
  NIO Watch   500ms wait   TestFileDetector  BuildTool+Parse   TaskPipeline  User feedback
```

### Error Handling

Watch mode is designed for resilience:

- **File system errors**: Logged, watch continues
- **Test detection errors**: Callback notified, watch continues
- **Implementation errors**: Callback notified with details, watch continues
- **Unexpected errors**: Logged, attempt recovery

### Integration with DefaultJwrightCore

```java
@Override
public WatchHandle watch(WatchRequest request, WatchCallback callback) {
    return fileWatcherService.watch(request, callback);
}
```

Simple delegation to FileWatcherService maintains separation of concerns.

---

**Last Updated:** 2025-01-15
**Status:** Implemented and tested
