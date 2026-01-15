# jwright

AI-assisted Test-Driven Development tool using local language models.

**Core Principle:** "Don't make the model use tools. Make tools wrap the model."

jwright generates implementation code from failing tests, keeping you in control of the TDD cycle while leveraging AI for the mechanical work of writing code.

## Features

- **Privacy-first**: Uses local LLMs via Ollama - your code never leaves your machine
- **Atomic TDD**: One test, one implementation - maintains TDD discipline
- **Human oversight**: Review and approve all generated code
- **Smart context**: Extracts test assertions, mocks, type definitions to guide generation
- **Auto-refactoring**: Optional code improvement pass after implementation
- **Watch mode**: Continuous TDD - implement as you write tests

## Prerequisites

- **Java 21** or later
- **Maven 3.8+**
- **Ollama** with a code model installed:
  ```bash
  # Install Ollama from https://ollama.ai
  ollama pull cogito:8b
  ```

### Setting Up cogito:8b-8k (Recommended)

The default `cogito:8b` model uses a 2k context window. For best results with jwright, create an 8k context variant:

```bash
# Start an Ollama chat session
ollama run cogito:8b

# In the chat, set the context size and save as new model
/set parameter num_ctx 8192
/save cogito:8b-8k

# Exit with Ctrl+D, then verify
ollama list | grep cogito:8b-8k
```

This 8k context version dramatically improves code generation for complex logic. See [MODEL_BENCHMARKS.md](MODEL_BENCHMARKS.md) for benchmark results showing cogito:8b-8k achieving 12/12 tests while the standard 2k version only achieves 1/12.

### Model Selection

jwright works with any Ollama model, but performance varies significantly. Our [benchmark testing](MODEL_BENCHMARKS.md) found:

| Model | Size | Recommendation |
|-------|------|----------------|
| **cogito:8b-8k** | 4.9 GB | Best overall - handles complex logic |
| qwen2.5-coder:14b | 9.0 GB | Good for standard TDD tasks |
| phi4-mini:latest | 2.5 GB | Budget option for simple methods |

See [MODEL_BENCHMARKS.md](MODEL_BENCHMARKS.md) for detailed performance analysis across 11 models.

## Installation

```bash
# Clone the repository
git clone <repository-url>
cd jwright

# Build the project
mvn clean install

# Optional: Add to PATH for global access
ln -s "$(pwd)/jwright" ~/.local/bin/jwright
```

The `jwright` wrapper script in the repo root handles JAR discovery automatically.

## Quick Start

### 1. Initialize your project

```bash
cd your-java-project
/path/to/jwright init
# Or if added to PATH:
jwright init
```

This creates `.jwright/config.yaml` with default settings.

### 2. Write a failing test

```java
// src/test/java/com/example/CalculatorTest.java
@Test
void add_returnsSumOfTwoNumbers() {
    Calculator calc = new Calculator();
    int result = calc.add(2, 3);
    assertThat(result).isEqualTo(5);
}
```

### 3. Generate the implementation

```bash
jwright implement "CalculatorTest#add_returnsSumOfTwoNumbers"
```

You can use either simple class names or fully qualified names:
- `CalculatorTest#method` - jwright searches for the test file automatically
- `com.example.CalculatorTest#method` - uses exact package path

jwright will:
1. Parse your test to understand what's expected
2. Generate implementation code using the LLM
3. Write the code to your implementation file
4. Compile and run the test to verify it passes
5. Optionally refactor the code

### 4. Watch mode (continuous TDD)

```bash
jwright watch
```

jwright watches for test file changes and automatically implements failing tests in real-time.

**What it does:**
- Monitors test directories for changes
- Detects when test files are modified or created
- Identifies failing tests automatically
- Runs the implementation pipeline for each failing test
- Continues watching until you stop it (Ctrl+C)

**Example workflow:**
1. Start watch mode in your project
2. Write or modify a test
3. Save the file
4. jwright detects the change, finds failing tests, and generates implementations
5. See results immediately in your terminal

## Commands

### `jwright init`

Initialize jwright configuration in your project.

```bash
jwright init [-d <project-dir>]
```

Creates `.jwright/config.yaml` and `.jwright/templates/` directory.

### `jwright implement`

Generate implementation for a specific test method.

```bash
jwright implement <TestClass#testMethod> [options]

Options:
  -d, --directory    Project directory (default: current directory)
  --dry-run          Show what would be done without making changes
  --verbose          Enable verbose output
  --no-refactor      Skip the refactoring pass
```

Example:
```bash
jwright implement "GameTest#addPlayer_addsPlayer" -d ~/projects/my-game
```

### `jwright watch`

Watch for test changes and implement automatically (continuous TDD mode).

```bash
jwright watch [options]

Options:
  -d, --directory    Project directory (default: current directory)
  --verbose          Enable verbose output
```

**How it works:**
1. Monitors test paths defined in `.jwright/config.yaml`
2. Debounces file changes (500ms default) to avoid duplicate processing
3. Automatically detects failing tests when files change
4. Runs implementation for each failing test
5. Reports results in real-time
6. Continues until interrupted with Ctrl+C

**Example output:**
```
[WATCH] Monitoring: src/test/java
[WATCH] Detected change: src/test/java/com/example/CalculatorTest.java
[WATCH] Found failing test: CalculatorTest#add_returnsSumOfTwoNumbers
[IMPLEMENT] Generating implementation...
[SUCCESS] Implementation completed in 2.3s
[WATCH] Waiting for changes...
```

## Configuration

Configuration is stored in `.jwright/config.yaml`:

```yaml
jwright:
  llm:
    provider: ollama
    ollama:
      url: http://localhost:11434
      model: cogito:8b-8k
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
```

### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `llm.provider` | LLM provider to use | `ollama` |
| `llm.ollama.url` | Ollama server URL | `http://localhost:11434` |
| `llm.ollama.model` | Model to use for generation | `cogito:8b-8k` |
| `tasks.implement.max-retries` | Max retry attempts on failure | `5` |
| `tasks.refactor.enabled` | Enable auto-refactoring | `true` |
| `watch.debounce` | Debounce time for file changes | `500ms` |

## How It Works

1. **Test Parsing**: Extracts test method body, assertions, mock setups, and type information
2. **Context Building**: Gathers implementation class fields, available methods, and hints
3. **Prompt Generation**: Renders a structured prompt using Mustache templates
4. **LLM Generation**: Sends prompt to local Ollama instance
5. **Code Injection**: Writes generated code into the target method
6. **Validation**: Compiles and runs the test to verify correctness
7. **Retry Loop**: On failure, includes error context and retries (up to max-retries)
8. **Refactoring**: Optionally improves the code while keeping tests green

## Architecture

jwright follows a modular architecture with clear stability tiers:

| Module | Purpose | Stability |
|--------|---------|-----------|
| `jwright-core` | Stable interfaces and contracts | STABLE |
| `jwright-engine` | Pipeline orchestration | INTERNAL |
| `jwright-java` | Java language support | EXTENSION |
| `jwright-ollama` | Ollama LLM provider | EXTENSION |
| `jwright-maven` | Maven build tool integration | EXTENSION |
| `jwright-gradle` | Gradle build tool integration | EXTENSION |
| `jwright-cli` | Command-line interface | INTERNAL |

For detailed architecture documentation, see [CLAUDE.md](CLAUDE.md) and the `.claude/systems/` directory.

## Customization

### Custom Templates

Override the default prompts by creating templates in `.jwright/templates/`:

- `implement.mustache` - Implementation generation prompt
- `refactor.mustache` - Refactoring prompt

### Adding Hints

Use the `@JwrightHint` annotation to guide generation:

```java
@Test
@JwrightHint("Use recursion for factorial calculation")
void factorial_calculatesCorrectly() {
    // ...
}
```

## Troubleshooting

### Ollama not running

```
Error: Connection refused to localhost:11434
```

Start Ollama:
```bash
ollama serve
```

### Model not found

```
Error: model 'cogito:8b-8k' not found
```

Create the model (see [Setting Up cogito:8b-8k](#setting-up-cogito8b-8k-recommended) above).

### Compilation failures after generation

The generated code may have compilation errors. jwright will automatically retry with the error context. If retries are exhausted, check:

1. Does the implementation class exist with the correct method signature?
2. Are all required imports available?
3. Is the test correctly structured?

### Test failures after generation

If generated code compiles but tests fail, jwright will retry with test failure information. Ensure your test assertions are clear and unambiguous.

## Contributing

See [CLAUDE.md](CLAUDE.md) for development guidelines and architecture documentation.

**Extending jwright:** See [EXTENDING.md](EXTENDING.md) for guides on adding:
- New LLM providers (OpenAI, Claude, etc.)
- New build tools (Bazel, etc.)
- New language support (Kotlin, etc.)

Key points:
- Never modify STABLE interfaces in `jwright-core`
- Add new functionality via new classes with `@Component` and `@Order`
- Follow the extractor and task ordering conventions

## License

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Acknowledgments

Built with:
- [Ollama](https://ollama.ai) - Local LLM runtime
- [JavaParser](https://javaparser.org) - Java AST parsing
- [Mustache](https://github.com/spullara/mustache.java) - Template engine
- [Picocli](https://picocli.info) - CLI framework
- [Spring Boot](https://spring.io/projects/spring-boot) - Dependency injection
