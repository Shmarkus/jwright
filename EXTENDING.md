# Extending jwright

This guide explains how to add new LLM providers, build tools, and language support to jwright.

## Architecture Overview

jwright uses a plugin architecture with three extension points:

| Extension Point | Interface | Example |
|-----------------|-----------|---------|
| LLM Provider | `LlmClient` | Ollama, Claude, OpenAI |
| Build Tool | `BuildTool` | Maven, Gradle |
| Language Support | `ContextExtractor`, `CodeWriter` | Java, Kotlin |

All extensions use Spring Boot's component scanning with conditional activation.

## Extension Pattern

Every extension follows the same pattern:

```java
@Component
@ConditionalOnProperty(name = "jwright.xxx.provider", havingValue = "myextension")
public class MyExtension implements ExtensionInterface {
    // Implementation
}
```

## Adding an LLM Provider

### 1. Create a new module

```
jwright-openai/
├── pom.xml
└── src/main/java/ee/jwright/openai/
    ├── OpenAiClient.java
    └── OpenAiConfig.java
```

### 2. Add dependency to parent pom.xml

```xml
<module>jwright-openai</module>
```

### 3. Implement LlmClient

```java
package ee.jwright.openai;

import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "jwright.llm.provider", havingValue = "openai")
@EnableConfigurationProperties(OpenAiConfig.class)
public class OpenAiClient implements LlmClient {

    private final OpenAiConfig config;

    public OpenAiClient(OpenAiConfig config) {
        this.config = config;
    }

    @Override
    public String getId() {
        return "openai";
    }

    @Override
    public String generate(String prompt) throws LlmException {
        // Call OpenAI API
        // Return generated code
        // Throw LlmException on errors
    }

    @Override
    public boolean isAvailable() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }
}
```

### 4. Create configuration class

```java
package ee.jwright.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "jwright.llm.openai")
public class OpenAiConfig {
    private String apiKey;
    private String model = "gpt-4-turbo";
    private Duration timeout = Duration.ofSeconds(120);

    // Getters and setters
}
```

### 5. User configuration

```yaml
# .jwright/config.yaml
jwright:
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4-turbo
      timeout: 120s
```

### Error Handling

Map provider errors to `LlmException.ErrorCode`:

| Scenario | ErrorCode |
|----------|-----------|
| Connection failed | `UNAVAILABLE` |
| Request timeout | `TIMEOUT` |
| Rate limited (429) | `RATE_LIMITED` |
| Context too large | `CONTEXT_EXCEEDED` |
| Parse failure | `INVALID_RESPONSE` |

## Adding a Build Tool

### 1. Create module

```
jwright-bazel/
├── pom.xml
└── src/main/java/ee/jwright/bazel/
    └── BazelBuildTool.java
```

### 2. Implement BuildTool

```java
package ee.jwright.bazel;

import ee.jwright.core.build.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.nio.file.Path;

@Component
@Order(200)  // After Maven (100), Gradle (150)
public class BazelBuildTool implements BuildTool {

    @Override
    public String getId() {
        return "bazel";
    }

    @Override
    public boolean supports(Path projectDir) {
        // Check for BUILD or BUILD.bazel file
        return Files.exists(projectDir.resolve("BUILD"))
            || Files.exists(projectDir.resolve("BUILD.bazel"));
    }

    @Override
    public CompilationResult compile(Path projectDir) {
        // Run: bazel build //...
        // Parse output for errors
        // Return CompilationResult
    }

    @Override
    public TestResult runTest(Path projectDir, String testClass, String testMethod) {
        // Run: bazel test //path:test --test_filter=method
        // Parse output
        // Return TestResult
    }
}
```

### Build Tool Order

| Order | Build Tool | Detection |
|-------|------------|-----------|
| 100 | Maven | `pom.xml` |
| 150 | Gradle | `build.gradle` or `build.gradle.kts` |
| 200+ | Custom | Your detection logic |

First matching build tool wins.

## Adding Language Support

Language support requires two components:

### 1. Context Extractors

Extract information from test files:

```java
package ee.jwright.kotlin.extract;

import ee.jwright.core.extract.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(150)  // After Java extractors
public class KotlinTestMethodExtractor implements ContextExtractor {

    @Override
    public int order() {
        return 150;
    }

    @Override
    public boolean supports(ExtractionRequest request) {
        return request.testFile().toString().endsWith(".kt");
    }

    @Override
    public void extract(ExtractionRequest request, ExtractionContext.Builder builder) {
        // Parse Kotlin test file
        // Extract test method body, assertions, etc.
        // Add to builder
    }
}
```

### Extractor Order Convention

| Range | Purpose |
|-------|---------|
| 100-199 | Test structure (method body, class info) |
| 200-299 | Assertions |
| 300-399 | Mocks |
| 400-499 | Hints (@JwrightHint) |
| 500-599 | Implementation analysis |
| 600-699 | Type definitions |
| 700-799 | Method signatures |

### 2. Code Writer

Write generated code to implementation files:

```java
package ee.jwright.kotlin.write;

import ee.jwright.core.write.*;
import org.springframework.stereotype.Component;

@Component
public class KotlinCodeWriter implements CodeWriter {

    @Override
    public String getId() {
        return "kotlin";
    }

    @Override
    public boolean supports(Path implFile) {
        return implFile.toString().endsWith(".kt");
    }

    @Override
    public void writeMethod(Path implFile, String methodName, String code) {
        // Parse Kotlin file
        // Find or create method
        // Insert generated code
        // Write back to file
    }
}
```

## Testing Your Extension

### Unit Tests

```java
@Test
void generate_returnsCode_whenApiSucceeds() {
    // Mock HTTP client
    // Call generate()
    // Verify response parsing
}

@Test
void generate_throwsLlmException_whenApiTimesOut() {
    // Mock timeout
    // Verify LlmException with TIMEOUT code
}
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = "jwright.llm.provider=openai")
class OpenAiIntegrationTest {

    @Autowired
    private LlmClient client;

    @Test
    void clientIsOpenAi() {
        assertThat(client.getId()).isEqualTo("openai");
    }
}
```

## Module Dependencies

```xml
<!-- Your extension pom.xml -->
<dependencies>
    <dependency>
        <groupId>ee.jwright</groupId>
        <artifactId>jwright-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

## Stability Rules

| Tier | Rule |
|------|------|
| **STABLE** (jwright-core interfaces) | Never change signatures |
| **INTERNAL** (jwright-engine) | Can evolve, honor contracts |
| **EXTENSION** (your module) | Independent lifecycle |

If you think you need to change a STABLE interface, you're solving the wrong problem.

## Checklist

- [ ] Create module under `jwright-xxx/`
- [ ] Implement required interface(s)
- [ ] Add `@Component` and conditional activation
- [ ] Create configuration class if needed
- [ ] Write unit tests
- [ ] Write integration test
- [ ] Add to parent pom.xml modules list
- [ ] Document configuration in README

## Examples

See existing implementations:

| Extension | Module | Key Files |
|-----------|--------|-----------|
| Ollama LLM | `jwright-ollama` | `OllamaClient.java`, `OllamaConfig.java` |
| Maven | `jwright-maven` | `MavenBuildTool.java` |
| Java | `jwright-java` | `extract/*.java`, `write/*.java` |
