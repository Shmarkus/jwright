# jwright-cli - Command Line Interface

> **Role**: User-facing CLI for jwright commands
> **Module**: `jwright-cli`
> **Package**: `ee.jwright.cli.*`
> **Stability**: EXTENSION - User interface layer
> **Critical Path**: Yes - Primary user interaction

## Topology
| Direction | Connected To | Interface | Purpose |
|-----------|--------------|-----------|---------|
| ← Depends | jwright-core | `JwrightCore` | API calls |
| ← Depends | jwright-engine | Runtime | Default implementation |
| → Outputs | Terminal | stdout/stderr | User feedback |

## Quick Health
```bash
# Show version
jwright --version

# Show help
jwright --help

# Test connectivity
jwright init --dry-run
```

## Key Commands
- `jwright init` - Initialize project configuration
- `jwright implement <Test#method>` - Generate implementation
- `jwright watch` - Watch mode for continuous TDD

---
<!-- WARM CONTEXT ENDS ABOVE THIS LINE -->

## Full Documentation

### Package Structure

```
ee.jwright.cli/
├── JwrightCli.java           # Main entry point
├── InitCommand.java          # init command
├── ImplementCommand.java     # implement command
└── WatchCommand.java         # watch command
```

### Command Reference

#### `jwright init`

Initialize jwright configuration for a project.

```bash
jwright init [--dir <path>]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--dir <path>` | Project directory | Current directory |

**Creates:**
- `.jwright/config.yaml` - Configuration file
- `.jwright/templates/` - Custom template directory

#### `jwright implement`

Generate implementation for a failing test.

```bash
jwright implement <TestClass#testMethod> [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--no-refactor` | Skip refactoring step | false |
| `--dry-run` | Show code without writing | false |
| `--verbose` | Show prompts and responses | false |
| `--quiet` | Minimal output | false |

**Examples:**
```bash
# Basic usage
jwright implement UserServiceTest#shouldCreateUser

# Skip optional tasks
jwright implement UserServiceTest#shouldCreateUser --no-refactor

# Preview without changes
jwright implement UserServiceTest#shouldCreateUser --dry-run

# Debug mode
jwright implement UserServiceTest#shouldCreateUser --verbose
```

#### `jwright watch`

Watch for test file changes and auto-implement.

```bash
jwright watch [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--dir <path>` | Project directory | Current directory |
| `--verbose` | Show details | false |
| `--quiet` | Minimal output | false |

**Behavior:**
1. Watches paths defined in `config.yaml` (default: `src/test/java`)
2. Detects new/modified test files (debounced to 500ms)
3. Finds failing tests in changed files
4. Runs implementation pipeline for each failing test
5. Reports results in real-time
6. Continues watching until interrupted (Ctrl+C)

**Example Output:**
```
[WATCH] Monitoring: src/test/java
[WATCH] Detected change: src/test/java/com/example/CalculatorTest.java
[WATCH] Found failing test: CalculatorTest#add_returnsSumOfTwoNumbers
[IMPLEMENT] Generating implementation...
[SUCCESS] Implementation completed in 2.3s
[WATCH] Waiting for changes...
```

#### Global Options

```bash
jwright --version    # Show version
jwright --help       # Show help
```

### Log Levels

| Flag | Level | Shows |
|------|-------|-------|
| (default) | INFO | Progress, results |
| `--quiet` | WARN | Warnings, errors only |
| `--verbose` | DEBUG | Prompts, responses, details |
| `--trace` | TRACE | Everything (dev only) |

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Implementation failed |
| 2 | Configuration error |
| 3 | Build tool not found |
| 4 | LLM unavailable |

### Implementation

#### JwrightCli

```java
@SpringBootApplication
public class JwrightCli implements CommandLineRunner {

    @Autowired
    private JwrightCore core;

    public static void main(String[] args) {
        SpringApplication.run(JwrightCli.class, args);
    }

    @Override
    public void run(String... args) {
        // Parse args, dispatch to command handlers
    }
}
```

Uses Spring Boot + Picocli for argument parsing.

#### ImplementCommand

```java
@Command(name = "implement")
public class ImplementCommand implements Runnable {

    @Parameters(index = "0")
    private String target;  // TestClass#testMethod

    @Option(names = "--no-refactor")
    private boolean noRefactor;

    @Option(names = "--dry-run")
    private boolean dryRun;

    @Option(names = "--verbose")
    private boolean verbose;

    @Override
    public void run() {
        ImplementRequest request = new ImplementRequest(
            Path.of("."),
            target,
            dryRun,
            verbose ? LogLevel.DEBUG : LogLevel.INFO
        );

        PipelineResult result = core.implement(request);

        if (result.success()) {
            // Print success, show generated code location
        } else {
            // Print failure details
            System.exit(1);
        }
    }
}
```

#### WatchCommand

Implemented and functional. Monitors test files and automatically triggers implementation.

```java
@Command(name = "watch")
public class WatchCommand implements Runnable {

    @Option(names = {"-d", "--directory"})
    private Path directory = Path.of(".");

    @Option(names = "--verbose")
    private boolean verbose;

    @Override
    public void run() {
        WatchRequest request = WatchRequest.builder()
            .projectRoot(directory)
            .logLevel(verbose ? LogLevel.DEBUG : LogLevel.INFO)
            .build();

        WatchHandle handle = core.watch(request, new WatchCallback() {
            @Override
            public void onTestDetected(String target) {
                System.out.println("[WATCH] Found failing test: " + target);
            }

            @Override
            public void onImplementationComplete(PipelineResult result) {
                if (result.success()) {
                    System.out.println("[SUCCESS] Implementation completed");
                } else {
                    System.err.println("[FAILED] " + result.message());
                }
            }

            @Override
            public void onError(JwrightException error) {
                System.err.println("[ERROR] " + error.getMessage());
            }
        });

        System.out.println("[WATCH] Monitoring: " + request.watchPaths());
        System.out.println("[WATCH] Press Ctrl+C to stop");

        // Block until interrupted
        Runtime.getRuntime().addShutdownHook(new Thread(handle::stop));

        // Keep main thread alive
        while (handle.isRunning()) {
            Thread.sleep(1000);
        }
    }
}
```

**Features:**
- Real-time file system monitoring
- Debounced change detection (prevents duplicate processing)
- Automatic failing test discovery
- Clean shutdown on Ctrl+C
- Configurable watch paths and ignore patterns

### Output Formatting

**Success:**
```
✓ ImplementTask: Generated calculateTotal() in 2.3s
✓ RefactorTask: Simplified conditionals
✓ Implementation written to src/main/java/com/example/OrderService.java
```

**Failure:**
```
✗ ImplementTask: Failed after 5 attempts
  Last error: Test assertion failed
    expected: 42
    actual: 0

  Generated code:
    public int calculateTotal() {
        return 0;  // TODO: implement
    }
```

**Dry Run:**
```
[DRY RUN] Would write to src/main/java/com/example/OrderService.java:

public int calculateTotal() {
    return items.stream()
        .mapToInt(Item::getPrice)
        .sum();
}
```

---

**Last Updated:** 2025-01-15
**Status:** Fully implemented and tested
