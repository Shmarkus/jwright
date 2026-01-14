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
| `--verbose` | Show details | false |
| `--quiet` | Minimal output | false |

**Behavior:**
1. Watches paths defined in `config.yaml`
2. Detects new/modified test files
3. Finds failing tests
4. Runs implementation pipeline
5. Reports results

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

```java
@Command(name = "watch")
public class WatchCommand implements Runnable {

    @Override
    public void run() {
        WatchRequest request = buildFromConfig();

        WatchHandle handle = core.watch(request, new WatchCallback() {
            @Override
            public void onTestDetected(String target) {
                System.out.println("Detected: " + target);
            }

            @Override
            public void onGenerationComplete(PipelineResult result) {
                // Print result summary
            }

            @Override
            public void onError(JwrightException error) {
                System.err.println("Error: " + error.getMessage());
            }
        });

        // Block until interrupted
        Runtime.getRuntime().addShutdownHook(new Thread(handle::stop));
    }
}
```

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

**Last Updated:** 2025-01-14
**Status:** Design complete
