# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

jwright is an AI-assisted Test-Driven Development (TDD) tool using local language models (Ollama). It generates code implementations from failing tests with human oversight.

**Core Principle:** "Don't make the model use tools. Make tools wrap the model."

**Status:** Implementation complete (88/88 tasks).

## Build Commands

```bash
# Build
mvn clean install

# Run tests
mvn test

# Single test
mvn test -Dtest=TestClassName#testMethodName

# Skip tests during install
mvn clean install -DskipTests
```

## Architecture

### Stability Tiers

| Tier | Components | Rule |
|------|------------|------|
| **★STABLE** | Interfaces in `jwright-core` | Never change. Extensions depend on these. |
| **INTERNAL** | `jwright-engine` implementations | Can evolve, must honor contracts. |
| **EXTENSION** | Language/LLM/build plugins | Independent lifecycle. |

**Critical rule:** If you think you need to change a STABLE contract, you're solving the wrong problem.

### Module Structure

- **jwright-core/** - Stable interfaces: `JwrightCore`, `Task`, `ContextExtractor`, `CodeWriter`, `LlmClient`, `BuildTool`, `TemplateEngine`
- **jwright-engine/** - Internal orchestration: `TaskPipeline`, `BackupManager`, `ContextBuilder`
- **jwright-java/** - Java language support (extractors, writers)
- **jwright-ollama/** - Ollama LLM provider
- **jwright-maven/** - Maven build tool
- **jwright-cli/** - CLI commands: `init`, `implement`, `watch`
- **jwright-bom/** - Bill of Materials

### Package Convention

All packages under `ee.jwright.*`

### Task Pipeline Order

| Range | Purpose |
|-------|---------|
| 100-199 | Generation (implement) |
| 200-299 | Improvement (refactor) |
| 300-399 | Quality (lint, checkstyle) |
| 400-499 | Formatting (spotless, prettier) |
| 500+ | Custom/third-party |

### Extractor Order Convention

| Range | Purpose |
|-------|---------|
| 100-199 | Test structure |
| 200-299 | Assertions |
| 300-399 | Mocks |
| 400-499 | Hints |
| 500-599 | Implementation analysis |
| 600-699 | Type definitions |
| 700-799 | Method signatures |

## Key Design Patterns

### Extension Pattern

New behavior = new class with `@Component`, `@Order`, and conditional activation:

```java
@Component
@Order(350)
@ConditionalOnProperty(name = "jwright.feature", havingValue = "enabled")
public class NewTask implements Task { }
```

### Data Flow

1. `ExtractionContext` is **immutable** - tasks read only
2. `PipelineState` is **mutable** - only `TaskPipeline` modifies
3. `BackupManager` owns file safety - all writes must be revertible
4. Templates in `.mustache` files, not code

### Required vs Optional Tasks

- **Required tasks** (e.g., `ImplementTask`): Failure stops pipeline
- **Optional tasks** (e.g., `RefactorTask`, `LintTask`): Failure triggers revert and continues

## Configuration

Project config lives in `.jwright/config.yaml`. Template resolution order:
1. `.jwright/templates/` (project)
2. `~/.jwright/templates/` (user)
3. Bundled defaults (JAR)

## Detailed System Documentation

For deeper architectural details, see `.claude/systems/`:

| Document | Description |
|----------|-------------|
| [core-api.md](.claude/systems/core-api.md) | ★STABLE interfaces and contracts |
| [engine.md](.claude/systems/engine.md) | Pipeline orchestration and internal components |
| [java-support.md](.claude/systems/java-support.md) | Java extractors and writers |
| [llm-providers.md](.claude/systems/llm-providers.md) | Ollama, Claude, and LLM integration |
| [build-tools.md](.claude/systems/build-tools.md) | Maven, Gradle build system integration |
| [cli.md](.claude/systems/cli.md) | Command line interface reference |

## Implementation Plan

See [.claude/IMPLEMENTATION_PLAN.md](.claude/IMPLEMENTATION_PLAN.md) for the completed implementation roadmap:

- **Phase 1:** Project Skeleton (9 tasks) ✓
- **Phase 2:** Core Contracts - ★STABLE interfaces (18 tasks) ✓
- **Phase 3:** Engine Foundation (19 tasks) ✓
- **Phase 4:** Build Tool - Maven (7 tasks) ✓
- **Phase 5:** LLM Provider - Ollama (5 tasks) ✓
- **Phase 6:** Java Language Support (12 tasks) ✓
- **Phase 7:** Core Tasks (7 tasks) ✓
- **Phase 8:** CLI (5 tasks) ✓
- **Phase 9:** Integration & Polish (6 tasks) ✓

**Total:** 88/88 tasks complete.
