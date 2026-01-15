package ee.jwright.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the Ollama LLM client.
 * <p>
 * These properties are bound from the application configuration under the
 * prefix {@code jwright.llm.ollama}.
 * </p>
 *
 * <h2>Example Configuration</h2>
 * <pre>
 * jwright:
 *   llm:
 *     ollama:
 *       url: http://localhost:11434
 *       model: cogito:8b-8k
 *       timeout: 120s
 * </pre>
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "jwright.llm.ollama")
public class OllamaConfig {

    /**
     * The URL of the Ollama server.
     * <p>Default: {@code http://localhost:11434}</p>
     */
    private String url = "http://localhost:11434";

    /**
     * The model to use for code generation.
     * <p>Default: {@code cogito:8b-8k}</p>
     */
    private String model = "cogito:8b-8k";

    /**
     * The timeout for LLM requests.
     * <p>Default: {@code 120 seconds}</p>
     */
    private Duration timeout = Duration.ofSeconds(120);

    /**
     * Returns the URL of the Ollama server.
     *
     * @return the Ollama server URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the URL of the Ollama server.
     *
     * @param url the Ollama server URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Returns the model to use for code generation.
     *
     * @return the model name
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the model to use for code generation.
     *
     * @param model the model name
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Returns the timeout for LLM requests.
     *
     * @return the request timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout for LLM requests.
     *
     * @param timeout the request timeout
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
