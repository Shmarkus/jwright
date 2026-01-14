package ee.jwright.core.contract;

import ee.jwright.core.llm.LlmClient;
import ee.jwright.core.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test class for verifying LlmClient contract compliance.
 * <p>
 * Extension developers should extend this class and implement {@link #createClient()}
 * to verify their LLM client implementation honors the stable contract.
 * </p>
 *
 * <p>Note: The {@link #generateShouldReturnNonNullResponse} test is marked as
 * optional since it requires an actual LLM connection. Override
 * {@link #skipIntegrationTests()} to return false to enable it.</p>
 */
@DisplayName("LlmClient Contract")
public abstract class AbstractLlmClientTest {

    protected LlmClient client;

    /**
     * Creates the LLM client implementation under test.
     *
     * @return the client to test
     */
    protected abstract LlmClient createClient();

    /**
     * Whether to skip integration tests that require actual LLM connection.
     * <p>
     * Override to return false in integration test suites.
     * </p>
     *
     * @return true to skip integration tests (default)
     */
    protected boolean skipIntegrationTests() {
        return true;
    }

    @BeforeEach
    void setUp() {
        client = createClient();
    }

    @Test
    @DisplayName("getId should return non-null non-empty identifier")
    void getIdShouldReturnNonEmptyIdentifier() {
        assertThat(client.getId())
            .isNotNull()
            .isNotBlank();
    }

    @Test
    @DisplayName("isAvailable should not throw")
    void isAvailableShouldNotThrow() {
        // Should not throw, regardless of actual availability
        boolean available = client.isAvailable();

        // Result is deterministic (at least for short time period)
        assertThat(client.isAvailable()).isEqualTo(available);
    }

    @Test
    @DisplayName("generate should return non-null response when available")
    void generateShouldReturnNonNullResponse() throws LlmException {
        if (skipIntegrationTests()) {
            return; // Skip integration test
        }

        if (client.isAvailable()) {
            String response = client.generate("Return the word 'hello'");
            assertThat(response).isNotNull();
        }
    }
}
