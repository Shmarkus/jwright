# jwright Implementation Plan

> Phased, incremental implementation following TDD principles.
> Each task produces a working, testable artifact.

## Guiding Principles

1. **Test first** - Write failing test, then implement
2. **One thing at a time** - Each task has single responsibility
3. **Always compiling** - Never leave the build broken
4. **Vertical slices** - Prefer end-to-end thin slices over horizontal layers

---

## Phase 1: Project Skeleton ✅

**Goal:** Maven multi-module project that compiles with empty modules.
**Status:** COMPLETED - All modules created, build verified successful

### 1.1 Create root POM ✅
- **Artifact:** `pom.xml` with module declarations
- **Test:** `mvn validate` passes
- **Scope:** Parent POM, Java 21, Spring Boot parent

### 1.2 Create jwright-bom module ✅
- **Artifact:** `jwright-bom/pom.xml`
- **Test:** `mvn install -pl jwright-bom` passes
- **Scope:** Version management for all modules

### 1.3 Create jwright-core module ✅
- **Artifact:** `jwright-core/pom.xml`, empty `ee.jwright.core` package
- **Test:** `mvn compile -pl jwright-core` passes
- **Scope:** Module structure only

### 1.4 Create jwright-engine module ✅
- **Artifact:** `jwright-engine/pom.xml`, depends on core
- **Test:** `mvn compile -pl jwright-engine` passes

### 1.5 Create jwright-java module ✅
- **Artifact:** `jwright-java/pom.xml`, depends on core
- **Test:** `mvn compile -pl jwright-java` passes

### 1.6 Create jwright-ollama module ✅
- **Artifact:** `jwright-ollama/pom.xml`, depends on core
- **Test:** `mvn compile -pl jwright-ollama` passes

### 1.7 Create jwright-maven module ✅
- **Artifact:** `jwright-maven/pom.xml`, depends on core
- **Test:** `mvn compile -pl jwright-maven` passes

### 1.8 Create jwright-cli module ✅
- **Artifact:** `jwright-cli/pom.xml`, depends on all
- **Test:** `mvn compile -pl jwright-cli` passes

### 1.9 Verify full build ✅
- **Artifact:** None (validation)
- **Test:** `mvn clean install` passes for all modules
- **Result:** BUILD SUCCESS - All 8 modules compiled and installed

---

## Phase 2: Core Contracts (★STABLE) ✅

**Goal:** All stable interfaces defined and compilable. These are FROZEN once released.
**Status:** COMPLETED - All contracts defined, 112 unit tests passing

### 2.1 JwrightException ✅
- **Artifact:** `JwrightException.java`, `ErrorCode` enum
- **Test:** Unit test creating and throwing each error code
- **Scope:** Exception hierarchy only

### 2.2 Core models - Task results ✅
- **Artifact:** `TaskStatus.java`, `TaskResult.java`
- **Test:** Unit test for record equality, status values
- **Scope:** Enums and records only

### 2.3 Core models - Pipeline results ✅
- **Artifact:** `PipelineResult.java`, `InitResult.java`
- **Test:** Unit test for `hasWarnings()` logic
- **Scope:** Records with helper methods

### 2.4 Core models - Requests ✅
- **Artifact:** `ImplementRequest.java`, `WatchRequest.java`, `LogLevel.java`
- **Test:** Unit test for record construction
- **Scope:** Request records

### 2.5 Task interface ✅
- **Artifact:** `Task.java` interface
- **Test:** Compile-only (interface has no logic)
- **Scope:** Interface definition

### 2.6 Extraction models ✅
- **Artifact:** `ExtractionRequest.java`, `Assertion.java`, `MockSetup.java`, `VerifyStatement.java`, `MethodSignature.java`, `TypeDefinition.java`
- **Test:** Unit tests for record construction and equality
- **Scope:** All extraction-related records

### 2.7 ExtractionContext with Builder ✅
- **Artifact:** `ExtractionContext.java` with nested `Builder`
- **Test:** Unit test building context, verifying immutability
- **Scope:** Immutable record + mutable builder

### 2.8 ContextExtractor interface ✅
- **Artifact:** `ContextExtractor.java`
- **Test:** Compile-only
- **Scope:** Interface definition

### 2.9 Write models ✅
- **Artifact:** `WriteRequest.java`, `WriteMode.java`, `WriteResult.java`
- **Test:** Unit test for `WriteResult.success()` and `failure()` factories
- **Scope:** Write-related types

### 2.10 CodeWriter interface ✅
- **Artifact:** `CodeWriter.java`
- **Test:** Compile-only
- **Scope:** Interface definition

### 2.11 LlmException ✅
- **Artifact:** `LlmException.java` with `ErrorCode` enum
- **Test:** Unit test creating each error type
- **Scope:** LLM-specific exception

### 2.12 LlmClient interface ✅
- **Artifact:** `LlmClient.java`
- **Test:** Compile-only
- **Scope:** Interface definition

### 2.13 Build models ✅
- **Artifact:** `CompilationResult.java`, `CompilationError.java`, `TestResult.java`, `TestFailure.java`
- **Test:** Unit tests for record construction
- **Scope:** Build result types

### 2.14 BuildTool interface ✅
- **Artifact:** `BuildTool.java`
- **Test:** Compile-only
- **Scope:** Interface definition

### 2.15 TemplateEngine interface ✅
- **Artifact:** `TemplateEngine.java`
- **Test:** Compile-only
- **Scope:** Interface definition

### 2.16 Watch callback models ✅
- **Artifact:** `WatchCallback.java`, `WatchHandle.java`
- **Test:** Compile-only
- **Scope:** Watch mode interfaces

### 2.17 JwrightCore interface ✅
- **Artifact:** `JwrightCore.java`
- **Test:** Compile-only
- **Scope:** Main API interface

### 2.18 Contract compliance test suite ✅
- **Artifact:** Abstract test classes for each interface
- **Test:** Tests that verify implementations honor contracts
- **Scope:** Test infrastructure for extensions

---

## Phase 3: Engine Foundation ✅

**Goal:** Internal components that orchestrate execution.
**Status:** COMPLETED - All components implemented, 57 unit tests passing

### 3.1 BackupManager - snapshot ✅
- **Artifact:** `BackupManager.java` with `snapshot()` method
- **Test:** Snapshot file, verify content stored
- **Scope:** Single file backup

### 3.2 BackupManager - revert ✅
- **Artifact:** `revertLast()`, `revertAll()` methods
- **Test:** Modify file, revert, verify original content
- **Scope:** Restore functionality

### 3.3 BackupManager - commit ✅
- **Artifact:** `commit()` method
- **Test:** Commit clears stack, subsequent revert is no-op
- **Scope:** Finalization

### 3.4 PipelineState - basic ✅
- **Artifact:** `PipelineState.java` with attempt tracking
- **Test:** Increment attempts, verify count, check `canRetry()`
- **Scope:** Attempt management

### 3.5 PipelineState - failure tracking ✅
- **Artifact:** `FailedAttempt.java`, `recordFailure()` method
- **Test:** Record failures, retrieve history
- **Scope:** Failure history

### 3.6 PipelineState - code tracking ✅
- **Artifact:** `setGeneratedCode()`, `getGeneratedCode()`
- **Test:** Store and retrieve generated code
- **Scope:** Code state

### 3.7 ContextBuilder - basic ✅
- **Artifact:** `ContextBuilder.java` Spring component
- **Test:** Build with zero extractors returns empty context
- **Scope:** Extractor orchestration skeleton

### 3.8 ContextBuilder - extractor ordering ✅
- **Artifact:** Extractor execution by `@Order`
- **Test:** Mock extractors verify execution order
- **Scope:** Ordering logic

### 3.9 ContextBuilder - supports filtering ✅
- **Artifact:** Only run extractors where `supports()` is true
- **Test:** Mock extractors, verify skipped when unsupported
- **Scope:** Filtering logic

### 3.10 MustacheTemplateEngine - basic ✅
- **Artifact:** `MustacheTemplateEngine.java`
- **Test:** Render simple template with variables
- **Scope:** Basic Mustache rendering

### 3.11 MustacheTemplateEngine - resolution order ✅
- **Artifact:** `MustacheResolver.java` with path resolution
- **Test:** Project template overrides bundled
- **Scope:** Template lookup chain

### 3.12 TaskPipeline - single task execution ✅
- **Artifact:** `TaskPipeline.java` with single task support
- **Test:** Run one task, verify result captured
- **Scope:** Basic orchestration

### 3.13 TaskPipeline - task ordering ✅
- **Artifact:** Execute tasks by `getOrder()`
- **Test:** Multiple tasks run in order
- **Scope:** Ordering

### 3.14 TaskPipeline - skip logic ✅
- **Artifact:** Respect `shouldRun()` return value
- **Test:** Task skipped when `shouldRun()` returns false
- **Scope:** Conditional execution

### 3.15 TaskPipeline - required task failure ✅
- **Artifact:** Pipeline fails when required task fails
- **Test:** Required task fails → pipeline fails, files reverted
- **Scope:** Failure handling

### 3.16 TaskPipeline - optional task failure ✅
- **Artifact:** Pipeline continues when optional task fails
- **Test:** Optional task fails → reverted, marked REVERTED, continues
- **Scope:** Optional task handling

### 3.17 TaskPipeline - retry logic ✅
- **Artifact:** Retry required tasks up to max
- **Test:** Task fails twice, succeeds third → success
- **Scope:** Retry mechanism

### 3.18 DefaultJwrightCore - init ✅
- **Artifact:** `DefaultJwrightCore.java` with `init()` method
- **Test:** Creates `.jwright/config.yaml` and templates dir
- **Scope:** Project initialization

### 3.19 DefaultJwrightCore - implement ✅
- **Artifact:** `implement()` method wiring
- **Test:** Integration test with mock components
- **Scope:** Main workflow

---

## Phase 4: Build Tool - Maven ✅

**Goal:** Working Maven integration for compile and test.
**Status:** COMPLETED - All 7 tasks implemented, 22 unit tests passing

### 4.1 MavenBuildTool - detection ✅
- **Artifact:** `MavenBuildTool.java` with `supports()`
- **Test:** Returns true for dir with `pom.xml`
- **Scope:** Detection only

### 4.2 MavenBuildTool - wrapper detection ✅
- **Artifact:** Use `./mvnw` when available
- **Test:** Prefers wrapper over system maven
- **Scope:** Command selection

### 4.3 MavenBuildTool - compile ✅
- **Artifact:** `compile()` method
- **Test:** Compile test project, verify success
- **Scope:** Compilation

### 4.4 MavenBuildTool - compile error parsing ✅
- **Artifact:** Parse Maven error output
- **Test:** Compile broken code, extract file/line/message
- **Scope:** Error extraction

### 4.5 MavenBuildTool - run test class ✅
- **Artifact:** `runTests(String testClass)`
- **Test:** Run passing test class, verify result
- **Scope:** Test execution

### 4.6 MavenBuildTool - run single test ✅
- **Artifact:** `runSingleTest(String testClass, String testMethod)`
- **Test:** Run single test method
- **Scope:** Targeted test execution

### 4.7 MavenBuildTool - test failure parsing ✅
- **Artifact:** Parse Surefire reports
- **Test:** Run failing test, extract failure details
- **Scope:** Failure extraction

---

## Phase 5: LLM Provider - Ollama ✅

**Goal:** Working Ollama integration for code generation.
**Status:** COMPLETED - All 5 tasks implemented, 21 unit tests passing

### 5.1 OllamaConfig ✅
- **Artifact:** `OllamaConfig.java` with `@ConfigurationProperties`
- **Test:** Config binds from yaml
- **Scope:** Configuration

### 5.2 OllamaClient - availability check ✅
- **Artifact:** `isAvailable()` method
- **Test:** Returns true when Ollama running (integration test)
- **Scope:** Health check

### 5.3 OllamaClient - generate ✅
- **Artifact:** `generate()` method
- **Test:** Generate simple response (integration test)
- **Scope:** Core generation

### 5.4 OllamaClient - timeout handling ✅
- **Artifact:** Timeout → `LlmException(TIMEOUT)`
- **Test:** Mock slow response, verify timeout exception
- **Scope:** Error handling

### 5.5 OllamaClient - unavailable handling ✅
- **Artifact:** Connection refused → `LlmException(UNAVAILABLE)`
- **Test:** No server, verify exception
- **Scope:** Error handling

---

## Phase 6: Java Language Support ✅

**Goal:** Extract context from Java tests, write Java code.
**Status:** COMPLETED - All 12 tasks implemented, 76 unit tests passing

### 6.1 JavaParserUtils - parse file ✅
- **Artifact:** `JavaParserUtils.java` with `parse()`
- **Test:** Parse valid Java file
- **Scope:** Parser wrapper

### 6.2 JavaParserUtils - find method ✅
- **Artifact:** `findMethod()` utility
- **Test:** Find method by name
- **Scope:** Method lookup

### 6.3 JavaTestMethodExtractor ✅
- **Artifact:** `JavaTestMethodExtractor.java`
- **Test:** Extract test class name, method name, body
- **Scope:** Test structure extraction

### 6.4 JavaAssertionExtractor - assertEquals ✅
- **Artifact:** Parse `assertEquals()` calls
- **Test:** Extract expected/actual from assertEquals
- **Scope:** JUnit assertions

### 6.5 JavaAssertionExtractor - assertThat (AssertJ) ✅
- **Artifact:** Parse AssertJ fluent assertions
- **Test:** Extract assertion chain
- **Scope:** AssertJ support

### 6.6 JavaMockitoExtractor - when/thenReturn ✅
- **Artifact:** Parse `when().thenReturn()` setups
- **Test:** Extract mock configuration
- **Scope:** Mock setup extraction

### 6.7 JavaMockitoExtractor - verify ✅
- **Artifact:** Parse `verify()` statements
- **Test:** Extract verification expectations
- **Scope:** Verify extraction

### 6.8 JavaHintExtractor ✅
- **Artifact:** Parse `@JwrightHint` annotations
- **Test:** Extract hint text from annotation
- **Scope:** Developer hints

### 6.9 JavaTypeDefinitionExtractor ✅
- **Artifact:** Extract types referenced in test
- **Test:** Find custom types, extract fields
- **Scope:** Type context

### 6.10 JavaMethodSignatureExtractor ✅
- **Artifact:** Extract available methods on collaborators
- **Test:** Find methods on mock objects
- **Scope:** Method context

### 6.11 JavaMethodBodyWriter - inject ✅
- **Artifact:** `JavaMethodBodyWriter.java` with INJECT mode
- **Test:** Inject body into empty method
- **Scope:** Body insertion

### 6.12 JavaMethodBodyWriter - replace ✅
- **Artifact:** REPLACE mode
- **Test:** Replace existing method body
- **Scope:** Body replacement

---

## Phase 7: Core Tasks ✅

**Goal:** ImplementTask and RefactorTask working end-to-end.
**Status:** COMPLETED - All 7 tasks implemented, tests passing

### 7.1 Implement prompt template ✅
- **Artifact:** `implement.mustache` template
- **Test:** Render with sample context
- **Scope:** Prompt generation

### 7.2 ImplementTask - basic generation ✅
- **Artifact:** `ImplementTask.java` generating code
- **Test:** Generate simple method body from test
- **Scope:** Core task

### 7.3 ImplementTask - compile validation ✅
- **Artifact:** Verify generated code compiles
- **Test:** Bad code triggers retry
- **Scope:** Validation

### 7.4 ImplementTask - test validation ✅
- **Artifact:** Verify test passes with generated code
- **Test:** Wrong code triggers retry
- **Scope:** Test verification

### 7.5 ImplementTask - error feedback ✅
- **Artifact:** Include previous errors in retry prompt
- **Test:** Second attempt sees first failure
- **Scope:** Iterative improvement

### 7.6 Refactor prompt template ✅
- **Artifact:** `refactor.mustache` template
- **Test:** Render with generated code
- **Scope:** Refactor prompting

### 7.7 RefactorTask ✅
- **Artifact:** `RefactorTask.java`
- **Test:** Refactors working code, keeps tests passing
- **Scope:** Code improvement

---

## Phase 8: CLI ✅

**Goal:** Working command-line interface.
**Status:** COMPLETED - All 5 tasks implemented, tests passing

### 8.1 CLI skeleton with Picocli ✅
- **Artifact:** `JwrightCli.java` main class
- **Test:** `--help` shows commands
- **Scope:** CLI framework

### 8.2 InitCommand ✅
- **Artifact:** `InitCommand.java`
- **Test:** `jwright init` creates config
- **Scope:** Init command

### 8.3 ImplementCommand - basic ✅
- **Artifact:** `ImplementCommand.java`
- **Test:** Runs implementation pipeline
- **Scope:** Core command

### 8.4 ImplementCommand - options ✅
- **Artifact:** `--dry-run`, `--verbose`, `--no-refactor`
- **Test:** Each option affects behavior
- **Scope:** Command options

### 8.5 Exit codes ✅
- **Artifact:** Proper exit codes for each scenario
- **Test:** Verify exit codes match spec
- **Scope:** Error signaling

---

## Phase 9: Integration & Polish ✅

**Goal:** End-to-end working system.
**Status:** COMPLETED - All 6 tasks implemented, tests passing

### 9.1 End-to-end test - simple method ✅
- **Artifact:** Integration test generating `add(a, b)`
- **Test:** Full pipeline from test to passing code
- **Scope:** Happy path

### 9.2 End-to-end test - with mocks ✅
- **Artifact:** Integration test with Mockito
- **Test:** Generate code using mock context
- **Scope:** Mock handling

### 9.3 End-to-end test - retry scenario ✅
- **Artifact:** Integration test where first attempt fails
- **Test:** Verify retry produces working code
- **Scope:** Retry flow

### 9.4 WatchCommand ✅
- **Artifact:** `WatchCommand.java`
- **Test:** Detects file change, triggers implementation
- **Scope:** Watch mode

### 9.5 Default configuration ✅
- **Artifact:** Sensible defaults in bundled config
- **Test:** Works out of box with Ollama
- **Scope:** User experience

### 9.6 Error messages ✅
- **Artifact:** Clear, actionable error messages
- **Test:** Each error scenario has helpful message
- **Scope:** User experience

---

## Implementation Order Summary

```
Phase 1: Project Skeleton         [9 tasks]   - Foundation                  ✅ COMPLETED
Phase 2: Core Contracts          [18 tasks]   - ★STABLE interfaces          ✅ COMPLETED
Phase 3: Engine Foundation       [19 tasks]   - Internal orchestration      ✅ COMPLETED
Phase 4: Build Tool - Maven       [7 tasks]   - First build integration     ✅ COMPLETED
Phase 5: LLM Provider - Ollama    [5 tasks]   - First LLM integration       ✅ COMPLETED
Phase 6: Java Language Support   [12 tasks]   - First language support      ✅ COMPLETED
Phase 7: Core Tasks               [7 tasks]   - Main pipeline tasks         ✅ COMPLETED
Phase 8: CLI                      [5 tasks]   - User interface              ✅ COMPLETED
Phase 9: Integration & Polish     [6 tasks]   - End-to-end validation       ✅ COMPLETED
                                 -----------
                          Total: 88 tasks
                      Completed: 88 tasks (100%)

PROJECT COMPLETE!
```

## Task Checklist Format

Each task when implemented:
- [ ] Write failing test
- [ ] Implement minimum code to pass
- [ ] Refactor if needed
- [ ] Verify `mvn test` passes
- [ ] Commit with descriptive message
