package ee.jwright.core.llm;

/**
 * A pluggable LLM client for code generation.
 * <p>
 * Implementations connect to various LLM providers (Ollama, Claude, OpenAI, etc.)
 * to generate code based on prompts.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see LlmException
 */
public interface LlmClient {

    /**
     * Returns the unique identifier for this LLM client.
     * <p>
     * Used for configuration and logging.
     * Examples: "ollama", "claude", "openai"
     * </p>
     *
     * @return the client identifier
     */
    String getId();

    /**
     * Generates a response for the given prompt.
     * <p>
     * The prompt should contain all context needed for code generation.
     * The response typically contains the generated code, possibly with markdown
     * code block markers that should be stripped by the caller.
     * </p>
     *
     * @param prompt the prompt to send to the LLM
     * @return the generated response
     * @throws LlmException if the generation fails
     */
    String generate(String prompt) throws LlmException;

    /**
     * Checks if this LLM client is available and ready to use.
     * <p>
     * For local models (Ollama), this checks if the server is running.
     * For cloud models, this checks if API credentials are configured.
     * </p>
     *
     * @return true if the client is available
     */
    boolean isAvailable();
}
