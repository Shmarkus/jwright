package ee.jwright.ollama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OllamaConfig}.
 */
class OllamaConfigTest {

    @Nested
    @DisplayName("Default Values")
    class DefaultValuesTest {

        @Test
        @DisplayName("url defaults to http://localhost:11434")
        void urlDefaultsToLocalhost() {
            OllamaConfig config = new OllamaConfig();
            assertThat(config.getUrl()).isEqualTo("http://localhost:11434");
        }

        @Test
        @DisplayName("model defaults to cogito:8b-8k")
        void modelDefaultsToCogito() {
            OllamaConfig config = new OllamaConfig();
            assertThat(config.getModel()).isEqualTo("cogito:8b-8k");
        }

        @Test
        @DisplayName("timeout defaults to 120 seconds")
        void timeoutDefaultsTo120Seconds() {
            OllamaConfig config = new OllamaConfig();
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(120));
        }
    }

    @Nested
    @DisplayName("Setters")
    class SettersTest {

        @Test
        @DisplayName("url can be set")
        void urlCanBeSet() {
            OllamaConfig config = new OllamaConfig();
            config.setUrl("http://remote:11434");
            assertThat(config.getUrl()).isEqualTo("http://remote:11434");
        }

        @Test
        @DisplayName("model can be set")
        void modelCanBeSet() {
            OllamaConfig config = new OllamaConfig();
            config.setModel("codellama:7b");
            assertThat(config.getModel()).isEqualTo("codellama:7b");
        }

        @Test
        @DisplayName("timeout can be set")
        void timeoutCanBeSet() {
            OllamaConfig config = new OllamaConfig();
            config.setTimeout(Duration.ofSeconds(60));
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("Spring Property Binding")
    @SpringBootTest(classes = OllamaConfigTest.PropertyBindingTestConfig.class)
    @TestPropertySource(properties = {
            "jwright.llm.ollama.url=http://custom:8080",
            "jwright.llm.ollama.model=llama2:13b",
            "jwright.llm.ollama.timeout=60s"
    })
    class PropertyBindingTest {

        @Autowired
        private OllamaConfig config;

        @Test
        @DisplayName("binds url from properties")
        void bindsUrl() {
            assertThat(config.getUrl()).isEqualTo("http://custom:8080");
        }

        @Test
        @DisplayName("binds model from properties")
        void bindsModel() {
            assertThat(config.getModel()).isEqualTo("llama2:13b");
        }

        @Test
        @DisplayName("binds timeout from properties")
        void bindsTimeout() {
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @EnableConfigurationProperties(OllamaConfig.class)
    static class PropertyBindingTestConfig {
        // Empty configuration class for Spring Boot test
    }
}
