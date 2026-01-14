# Build Tools - Build System Integration

> **Role**: Compile code and run tests via build systems
> **Modules**: `jwright-maven`, `jwright-gradle`
> **Package**: `ee.jwright.maven.*`, `ee.jwright.gradle.*`
> **Stability**: EXTENSION - Independent lifecycle
> **Critical Path**: Yes - Build verification required

## Topology
| Direction | Connected To | Interface | Purpose |
|-----------|--------------|-----------|---------|
| ← Depends | jwright-core | `BuildTool` | Implements contract |
| ← Receives | jwright-engine | Via Spring discovery | Auto-detection |
| → Calls | Maven CLI | Process | `mvn compile`, `mvn test` |
| → Calls | Gradle CLI | Process | `gradle compileJava`, `gradle test` |

## Quick Health
```bash
# Maven project
mvn --version
mvn compile -q

# Gradle project
gradle --version
gradle compileJava -q
```

## Key Components
- `MavenBuildTool`: Maven wrapper integration
- `GradleBuildTool`: Gradle wrapper integration
- Auto-detection via `pom.xml` or `build.gradle`

---
<!-- WARM CONTEXT ENDS ABOVE THIS LINE -->

## Full Documentation

### Maven Provider

```
ee.jwright.maven/
└── MavenBuildTool.java
```

#### MavenBuildTool

```java
@Component
@Order(100)  // Prefer Maven when both present
public class MavenBuildTool implements BuildTool {

    @Override
    public String getId() {
        return "maven";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean supports(Path projectDir) {
        return Files.exists(projectDir.resolve("pom.xml"));
    }

    @Override
    public CompilationResult compile(Path projectDir) {
        // Run: mvn compile -q
        // Parse output for errors
    }

    @Override
    public TestResult runTests(String testClass) {
        // Run: mvn test -Dtest=TestClass -q
    }

    @Override
    public TestResult runSingleTest(String testClass, String testMethod) {
        // Run: mvn test -Dtest=TestClass#testMethod -q
    }
}
```

**Maven Commands:**
| Operation | Command |
|-----------|---------|
| Compile | `mvn compile -q` |
| Test class | `mvn test -Dtest=TestClass -q` |
| Single test | `mvn test -Dtest=TestClass#testMethod -q` |
| Compile only | `mvn test-compile -q` |

### Gradle Provider

```
ee.jwright.gradle/
└── GradleBuildTool.java
```

#### GradleBuildTool

```java
@Component
@Order(200)  // Second priority after Maven
public class GradleBuildTool implements BuildTool {

    @Override
    public String getId() {
        return "gradle";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public boolean supports(Path projectDir) {
        return Files.exists(projectDir.resolve("build.gradle")) ||
               Files.exists(projectDir.resolve("build.gradle.kts"));
    }

    @Override
    public CompilationResult compile(Path projectDir) {
        // Run: gradle compileJava -q
    }

    @Override
    public TestResult runTests(String testClass) {
        // Run: gradle test --tests TestClass -q
    }

    @Override
    public TestResult runSingleTest(String testClass, String testMethod) {
        // Run: gradle test --tests TestClass.testMethod -q
    }
}
```

**Gradle Commands:**
| Operation | Command |
|-----------|---------|
| Compile | `gradle compileJava -q` |
| Test class | `gradle test --tests TestClass -q` |
| Single test | `gradle test --tests "TestClass.testMethod" -q` |
| Compile only | `gradle testClasses -q` |

### Result Models

```java
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

### Wrapper Detection

Prefer wrapper scripts when available:

```java
private String getMavenCommand(Path projectDir) {
    if (Files.exists(projectDir.resolve("mvnw"))) {
        return "./mvnw";
    }
    return "mvn";
}

private String getGradleCommand(Path projectDir) {
    if (Files.exists(projectDir.resolve("gradlew"))) {
        return "./gradlew";
    }
    return "gradle";
}
```

### Output Parsing

**Maven Compilation Error:**
```
[ERROR] /path/to/File.java:[10,5] cannot find symbol
```
Pattern: `\[ERROR\] (.+):\[(\d+),\d+\] (.+)`

**Gradle Compilation Error:**
```
e: /path/to/File.java:10:5: Unresolved reference: foo
```
Pattern: `e: (.+):(\d+):\d+: (.+)`

**Test Failure (both):**
Parse Surefire/JUnit XML reports in `target/surefire-reports/` or `build/test-results/`.

### Adding New Build Systems

```java
// jwright-bazel/src/.../BazelBuildTool.java
@Component
@Order(300)
public class BazelBuildTool implements BuildTool {

    @Override
    public boolean supports(Path projectDir) {
        return Files.exists(projectDir.resolve("BUILD")) ||
               Files.exists(projectDir.resolve("BUILD.bazel"));
    }

    @Override
    public CompilationResult compile(Path projectDir) {
        // Run: bazel build //...
    }

    @Override
    public TestResult runSingleTest(String testClass, String testMethod) {
        // Run: bazel test --test_filter=TestClass.testMethod //...
    }
}
```

---

**Last Updated:** 2025-01-14
**Status:** Design complete
