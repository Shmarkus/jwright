package ee.jwright.ollama;

import com.sun.net.httpserver.HttpServer;
import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OllamaClient}.
 */
class OllamaClientTest {

    @Nested
    @DisplayName("getId()")
    class GetIdTest {

        @Test
        @DisplayName("returns 'ollama'")
        void returnsOllama() {
            OllamaConfig config = new OllamaConfig();
            OllamaClient client = new OllamaClient(config);

            assertThat(client.getId()).isEqualTo("ollama");
        }
    }

    @Nested
    @DisplayName("implements LlmClient")
    class ImplementsLlmClientTest {

        @Test
        @DisplayName("OllamaClient implements LlmClient interface")
        void implementsLlmClient() {
            OllamaConfig config = new OllamaConfig();
            OllamaClient client = new OllamaClient(config);

            assertThat(client).isInstanceOf(LlmClient.class);
        }
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailableTest {

        private HttpServer server;
        private int port;

        @BeforeEach
        void setUp() throws IOException {
            // Create a simple HTTP server for testing
            server = HttpServer.create(new InetSocketAddress(0), 0);
            port = server.getAddress().getPort();
        }

        @AfterEach
        void tearDown() {
            if (server != null) {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("returns true when Ollama server responds to /api/tags")
        void returnsTrueWhenServerResponds() {
            // Setup mock server to respond to /api/tags
            server.createContext("/api/tags", exchange -> {
                String response = "{\"models\":[]}";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            OllamaClient client = new OllamaClient(config);

            assertThat(client.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("returns false when server is not running")
        void returnsFalseWhenServerNotRunning() {
            // Use a port where nothing is running
            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:59999");
            OllamaClient client = new OllamaClient(config);

            assertThat(client.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("returns false when server returns error status")
        void returnsFalseWhenServerReturnsError() {
            server.createContext("/api/tags", exchange -> {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            OllamaClient client = new OllamaClient(config);

            assertThat(client.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("generate()")
    class GenerateTest {

        private HttpServer server;
        private int port;

        @BeforeEach
        void setUp() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            port = server.getAddress().getPort();
        }

        @AfterEach
        void tearDown() {
            if (server != null) {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("sends POST request to /api/generate with correct body")
        void sendsPostWithCorrectBody() throws Exception {
            // Capture the request body for verification
            StringBuilder capturedBody = new StringBuilder();

            server.createContext("/api/generate", exchange -> {
                // Read the request body
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                capturedBody.append(new String(requestBody));

                // Return a valid response
                String response = "{\"response\":\"generated code\",\"done\":true}";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            config.setModel("test-model");
            OllamaClient client = new OllamaClient(config);

            client.generate("Write a function");

            // Verify the request body
            String body = capturedBody.toString();
            assertThat(body).contains("\"model\":\"test-model\"");
            assertThat(body).contains("\"prompt\":\"Write a function\"");
            assertThat(body).contains("\"stream\":false");
        }

        @Test
        @DisplayName("returns response text from API")
        void returnsResponseText() throws Exception {
            server.createContext("/api/generate", exchange -> {
                String response = "{\"response\":\"public void hello() { }\",\"done\":true}";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            OllamaClient client = new OllamaClient(config);

            String result = client.generate("Generate a hello method");

            assertThat(result).isEqualTo("public void hello() { }");
        }

        @Test
        @DisplayName("throws LlmException with INVALID_RESPONSE when JSON is malformed")
        void throwsOnMalformedJson() {
            server.createContext("/api/generate", exchange -> {
                String response = "not valid json";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            OllamaClient client = new OllamaClient(config);

            assertThatThrownBy(() -> client.generate("test"))
                    .isInstanceOf(LlmException.class)
                    .satisfies(e -> {
                        LlmException llmException = (LlmException) e;
                        assertThat(llmException.getCode()).isEqualTo(LlmException.ErrorCode.INVALID_RESPONSE);
                    });
        }

        @Test
        @DisplayName("throws LlmException with INVALID_RESPONSE when response field is missing")
        void throwsWhenResponseFieldMissing() {
            server.createContext("/api/generate", exchange -> {
                String response = "{\"done\":true}";  // Missing "response" field
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            OllamaClient client = new OllamaClient(config);

            assertThatThrownBy(() -> client.generate("test"))
                    .isInstanceOf(LlmException.class)
                    .satisfies(e -> {
                        LlmException llmException = (LlmException) e;
                        assertThat(llmException.getCode()).isEqualTo(LlmException.ErrorCode.INVALID_RESPONSE);
                    });
        }
    }

    @Nested
    @DisplayName("timeout handling")
    class TimeoutHandlingTest {

        private HttpServer server;
        private int port;

        @BeforeEach
        void setUp() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            port = server.getAddress().getPort();
        }

        @AfterEach
        void tearDown() {
            if (server != null) {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("throws LlmException with TIMEOUT when request exceeds configured timeout")
        void throwsTimeoutOnSlowResponse() {
            // Server that delays response longer than the configured timeout
            server.createContext("/api/generate", exchange -> {
                try {
                    // Sleep longer than the configured timeout
                    Thread.sleep(2000);  // 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String response = "{\"response\":\"slow response\",\"done\":true}";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            config.setTimeout(Duration.ofMillis(500));  // Very short timeout
            OllamaClient client = new OllamaClient(config);

            assertThatThrownBy(() -> client.generate("test"))
                    .isInstanceOf(LlmException.class)
                    .satisfies(e -> {
                        LlmException llmException = (LlmException) e;
                        assertThat(llmException.getCode()).isEqualTo(LlmException.ErrorCode.TIMEOUT);
                    });
        }

        @Test
        @DisplayName("uses configured timeout from OllamaConfig")
        void usesConfiguredTimeout() throws Exception {
            // Server that responds quickly
            server.createContext("/api/generate", exchange -> {
                String response = "{\"response\":\"quick response\",\"done\":true}";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.start();

            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:" + port);
            config.setTimeout(Duration.ofSeconds(30));  // Reasonable timeout
            OllamaClient client = new OllamaClient(config);

            // Should succeed with normal timeout
            String result = client.generate("test");
            assertThat(result).isEqualTo("quick response");
        }
    }

    @Nested
    @DisplayName("unavailable handling")
    class UnavailableHandlingTest {

        @Test
        @DisplayName("throws LlmException with UNAVAILABLE when server is not running")
        void throwsUnavailableWhenServerNotRunning() {
            // Use a port where nothing is running
            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:59999");  // No server on this port
            OllamaClient client = new OllamaClient(config);

            assertThatThrownBy(() -> client.generate("test"))
                    .isInstanceOf(LlmException.class)
                    .satisfies(e -> {
                        LlmException llmException = (LlmException) e;
                        assertThat(llmException.getCode()).isEqualTo(LlmException.ErrorCode.UNAVAILABLE);
                    });
        }

        @Test
        @DisplayName("includes server URL in error message")
        void includesUrlInErrorMessage() {
            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://localhost:59999");
            OllamaClient client = new OllamaClient(config);

            assertThatThrownBy(() -> client.generate("test"))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("http://localhost:59999");
        }
    }
}
