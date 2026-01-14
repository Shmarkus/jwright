package ee.jwright.ollama;

import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Ollama LLM client implementation.
 * <p>
 * This client connects to a local Ollama server to perform code generation.
 * It is automatically enabled when {@code jwright.llm.provider} is set to
 * "ollama" or when no provider is specified (default behavior).
 * </p>
 *
 * <h2>Configuration</h2>
 * <pre>
 * jwright:
 *   llm:
 *     provider: ollama  # or omit for default
 *     ollama:
 *       url: http://localhost:11434
 *       model: qwen2.5-coder:14b
 *       timeout: 120s
 * </pre>
 *
 * @since 1.0.0
 * @see OllamaConfig
 */
@Component
@ConditionalOnProperty(name = "jwright.llm.provider", havingValue = "ollama", matchIfMissing = true)
@EnableConfigurationProperties(OllamaConfig.class)
public class OllamaClient implements LlmClient {

    private final OllamaConfig config;
    private final HttpClient httpClient;

    /**
     * Creates a new OllamaClient with the specified configuration.
     *
     * @param config the Ollama configuration
     */
    public OllamaClient(OllamaConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getId() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String generate(String prompt) throws LlmException {
        String requestBody = buildRequestBody(prompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl() + "/api/generate"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .timeout(config.getTimeout())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return parseResponse(response.body());
        } catch (LlmException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new LlmException(LlmException.ErrorCode.TIMEOUT,
                    "Request to Ollama timed out after " + config.getTimeout().toSeconds() + " seconds", e);
        } catch (ConnectException e) {
            throw new LlmException(LlmException.ErrorCode.UNAVAILABLE,
                    "Cannot connect to Ollama server at " + config.getUrl(), e);
        } catch (Exception e) {
            // Check if the cause is a connection issue
            if (isConnectionRefused(e)) {
                throw new LlmException(LlmException.ErrorCode.UNAVAILABLE,
                        "Cannot connect to Ollama server at " + config.getUrl(), e);
            }
            throw new LlmException(LlmException.ErrorCode.UNKNOWN, "Failed to generate: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the exception indicates a connection was refused.
     *
     * @param e the exception to check
     * @return true if the connection was refused
     */
    private boolean isConnectionRefused(Exception e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Builds the JSON request body for the Ollama API.
     *
     * @param prompt the prompt to include in the request
     * @return the JSON request body
     */
    private String buildRequestBody(String prompt) {
        // Simple JSON construction without external dependencies
        // Escape special characters in the prompt
        String escapedPrompt = escapeJson(prompt);
        return String.format(
                "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
                escapeJson(config.getModel()),
                escapedPrompt
        );
    }

    /**
     * Escapes special characters for JSON string values.
     *
     * @param value the value to escape
     * @return the escaped value
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses the Ollama API response and extracts the generated text.
     *
     * @param responseBody the JSON response body
     * @return the generated text
     * @throws LlmException if the response is invalid
     */
    private String parseResponse(String responseBody) throws LlmException {
        try {
            // Simple JSON parsing for the "response" field
            // Response format: {"model":"...","created_at":"...","response":"...","done":true,...}
            int responseKeyIndex = responseBody.indexOf("\"response\"");
            if (responseKeyIndex == -1) {
                throw new LlmException(LlmException.ErrorCode.INVALID_RESPONSE,
                        "Missing 'response' field in Ollama response");
            }

            // Find the colon after "response"
            int colonIndex = responseBody.indexOf(':', responseKeyIndex);
            if (colonIndex == -1) {
                throw new LlmException(LlmException.ErrorCode.INVALID_RESPONSE,
                        "Malformed JSON: no colon after 'response' key");
            }

            // Find the start of the value (first quote after colon)
            int valueStart = responseBody.indexOf('"', colonIndex);
            if (valueStart == -1) {
                throw new LlmException(LlmException.ErrorCode.INVALID_RESPONSE,
                        "Malformed JSON: no value for 'response' key");
            }

            // Find the end of the value (handle escaped quotes)
            int valueEnd = findUnescapedQuote(responseBody, valueStart + 1);
            if (valueEnd == -1) {
                throw new LlmException(LlmException.ErrorCode.INVALID_RESPONSE,
                        "Malformed JSON: unterminated string value");
            }

            String rawValue = responseBody.substring(valueStart + 1, valueEnd);
            return unescapeJson(rawValue);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorCode.INVALID_RESPONSE,
                    "Failed to parse Ollama response: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the index of the next unescaped quote character.
     *
     * @param str   the string to search
     * @param start the starting index
     * @return the index of the unescaped quote, or -1 if not found
     */
    private int findUnescapedQuote(String str, int start) {
        boolean escaped = false;
        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Unescapes JSON string escape sequences.
     *
     * @param value the escaped value
     * @return the unescaped value
     */
    private String unescapeJson(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'b' -> { sb.append('\b'); i++; }
                    case 'f' -> { sb.append('\f'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'u' -> {
                        if (i + 5 < value.length()) {
                            String hex = value.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 5;
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
