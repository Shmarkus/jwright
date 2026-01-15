# LLM Providers - Language Model Integration

> **Role**: Abstraction layer for LLM providers
> **Modules**: `jwright-ollama`, `jwright-claude` (future)
> **Package**: `ee.jwright.ollama.*`, `ee.jwright.claude.*`
> **Stability**: EXTENSION - Independent lifecycle
> **Critical Path**: Yes - No LLM = no code generation

## Topology
| Direction | Connected To | Interface | Purpose |
|-----------|--------------|-----------|---------|
| ← Depends | jwright-core | `LlmClient` | Implements contract |
| ← Receives | jwright-engine | Via Spring discovery | Provider selection |
| → Calls | Ollama API | HTTP | Local LLM inference |
| → Calls | Claude API | HTTP | Cloud LLM inference |

## Quick Health
```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Test generation
curl http://localhost:11434/api/generate -d '{
  "model": "cogito:8b-8k",
  "prompt": "Hello",
  "stream": false
}'
```

## Key Components
- `OllamaClient`: Local LLM via Ollama API (default)
- `ClaudeClient`: Cloud LLM via Anthropic API (optional)
- Provider selection via `jwright.llm.provider` config

---
<!-- WARM CONTEXT ENDS ABOVE THIS LINE -->

## Full Documentation

### Ollama Provider (Default)

```
ee.jwright.ollama/
├── OllamaClient.java      # LlmClient implementation
└── OllamaConfig.java      # Configuration properties
```

#### OllamaClient

```java
@Component
@ConditionalOnProperty(name = "jwright.llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaClient implements LlmClient {

    private final OllamaConfig config;

    @Override
    public String getId() {
        return "ollama";
    }

    @Override
    public String generate(String prompt) throws LlmException {
        // POST to /api/generate
        // Handle timeouts, parse response
        // Map errors to LlmException.ErrorCode
    }

    @Override
    public boolean isAvailable() {
        // GET /api/tags, check for configured model
    }
}
```

#### OllamaConfig

```java
@ConfigurationProperties(prefix = "jwright.llm.ollama")
public class OllamaConfig {
    private String url = "http://localhost:11434";
    private String model = "cogito:8b-8k";
    private Duration timeout = Duration.ofSeconds(120);
}
```

### Claude Provider (Optional)

```
ee.jwright.claude/
├── ClaudeClient.java      # LlmClient implementation
└── ClaudeConfig.java      # Configuration properties
```

#### ClaudeClient

```java
@Component
@ConditionalOnProperty(name = "jwright.llm.provider", havingValue = "claude")
public class ClaudeClient implements LlmClient {

    private final ClaudeConfig config;

    @Override
    public String getId() {
        return "claude";
    }

    @Override
    public String generate(String prompt) throws LlmException {
        // POST to Anthropic Messages API
        // Use temperature: 0.0 for deterministic output
    }

    @Override
    public boolean isAvailable() {
        return config.getApiKey() != null;
    }
}
```

#### ClaudeConfig

```java
@ConfigurationProperties(prefix = "jwright.llm.claude")
public class ClaudeConfig {
    private String apiKey;                            // Required
    private String model = "claude-sonnet-4-20250514";
    private Duration timeout = Duration.ofSeconds(60);
    private Double temperature = 0.0;
}
```

### Configuration

```yaml
# .jwright/config.yaml
jwright:
  llm:
    provider: ollama    # or: claude, openai

    ollama:
      url: http://localhost:11434
      model: cogito:8b-8k
      timeout: 120s

    claude:
      api-key: ${CLAUDE_API_KEY}
      model: claude-sonnet-4-20250514
      timeout: 60s
```

### Error Handling

```java
public class LlmException extends Exception {
    public enum ErrorCode {
        TIMEOUT,           // Request took too long
        UNAVAILABLE,       // Provider not reachable
        RATE_LIMITED,      // Too many requests
        CONTEXT_EXCEEDED,  // Prompt too large
        INVALID_RESPONSE,  // Couldn't parse response
        UNKNOWN
    }
}
```

Map provider-specific errors:
| Ollama | Claude | ErrorCode |
|--------|--------|-----------|
| Connection refused | 503 | `UNAVAILABLE` |
| Read timeout | 504 | `TIMEOUT` |
| N/A | 429 | `RATE_LIMITED` |
| Model too small | 400 (context) | `CONTEXT_EXCEEDED` |

### Adding New Providers

```java
// jwright-openai/src/.../OpenAiClient.java
@Component
@ConditionalOnProperty(name = "jwright.llm.provider", havingValue = "openai")
public class OpenAiClient implements LlmClient {

    @Override
    public String getId() {
        return "openai";
    }

    @Override
    public String generate(String prompt) throws LlmException {
        // Use OpenAI Chat Completions API
        // Model: gpt-4, temperature: 0
    }

    @Override
    public boolean isAvailable() {
        return config.getApiKey() != null;
    }
}
```

### Recommended Models

| Provider | Model | Context | Use Case |
|----------|-------|---------|----------|
| Ollama | `cogito:8b-8k` | 32k | Default, local |
| Ollama | `codellama:13b` | 16k | Alternative |
| Claude | `claude-sonnet-4-20250514` | 200k | Cloud, larger context |
| OpenAI | `gpt-4-turbo` | 128k | Cloud, alternative |

### Privacy Considerations

- **Ollama (default)**: Code never leaves machine
- **Cloud providers**: Code sent to external API
- Configuration should warn when switching from local to cloud

---

**Last Updated:** 2025-01-14
**Status:** Design complete
