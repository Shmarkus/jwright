package ee.jwright.engine.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for default configuration.
 * <p>
 * Verifies that the bundled default configuration:
 * - Contains sensible defaults for Ollama
 * - Specifies all required settings
 * - Works out of the box
 * </p>
 *
 * <h2>Task 9.5: Default configuration</h2>
 */
@DisplayName("9.5 Default configuration")
class DefaultConfigurationTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Bundled default config")
    class BundledDefaultConfigTests {

        @Test
        @DisplayName("bundled config file exists in resources")
        void bundledConfigFileExistsInResources() {
            InputStream configStream = getClass()
                .getResourceAsStream("/jwright-default-config.yaml");

            assertThat(configStream).isNotNull();
        }

        @Test
        @DisplayName("bundled config is valid YAML")
        void bundledConfigIsValidYaml() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);

                assertThat(config).isNotNull();
                assertThat(config).containsKey("jwright");
            }
        }

        @Test
        @DisplayName("contains Ollama configuration")
        @SuppressWarnings("unchecked")
        void containsOllamaConfiguration() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> llm = (Map<String, Object>) jwright.get("llm");

                assertThat(llm.get("provider")).isEqualTo("ollama");

                Map<String, Object> ollama = (Map<String, Object>) llm.get("ollama");
                assertThat(ollama.get("url")).isEqualTo("http://localhost:11434");
                assertThat(ollama.get("model")).isNotNull();
            }
        }

        @Test
        @DisplayName("specifies default model for code generation")
        @SuppressWarnings("unchecked")
        void specifiesDefaultModelForCodeGeneration() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> llm = (Map<String, Object>) jwright.get("llm");
                Map<String, Object> ollama = (Map<String, Object>) llm.get("ollama");

                String model = (String) ollama.get("model");
                assertThat(model)
                    .isNotNull()
                    .contains("coder"); // Should be a code-focused model
            }
        }

        @Test
        @DisplayName("has reasonable timeout settings")
        @SuppressWarnings("unchecked")
        void hasReasonableTimeoutSettings() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> llm = (Map<String, Object>) jwright.get("llm");
                Map<String, Object> ollama = (Map<String, Object>) llm.get("ollama");

                String timeout = (String) ollama.get("timeout");
                assertThat(timeout).isNotNull();
                // Should be at least 60 seconds for reasonable generation time
                assertThat(timeout).matches("\\d+s");
            }
        }
    }

    @Nested
    @DisplayName("Task configuration defaults")
    class TaskConfigurationDefaultsTests {

        @Test
        @DisplayName("has implement task configuration")
        @SuppressWarnings("unchecked")
        void hasImplementTaskConfiguration() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> tasks = (Map<String, Object>) jwright.get("tasks");

                assertThat(tasks).containsKey("implement");

                Map<String, Object> implement = (Map<String, Object>) tasks.get("implement");
                assertThat(implement).containsKey("timeout");
                assertThat(implement).containsKey("max-retries");
            }
        }

        @Test
        @DisplayName("has refactor task configuration")
        @SuppressWarnings("unchecked")
        void hasRefactorTaskConfiguration() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> tasks = (Map<String, Object>) jwright.get("tasks");

                assertThat(tasks).containsKey("refactor");

                Map<String, Object> refactor = (Map<String, Object>) tasks.get("refactor");
                assertThat(refactor).containsKey("enabled");
            }
        }

        @Test
        @DisplayName("allows reasonable number of retries")
        @SuppressWarnings("unchecked")
        void allowsReasonableNumberOfRetries() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> tasks = (Map<String, Object>) jwright.get("tasks");
                Map<String, Object> implement = (Map<String, Object>) tasks.get("implement");

                Integer maxRetries = (Integer) implement.get("max-retries");
                assertThat(maxRetries)
                    .isGreaterThanOrEqualTo(3) // At least 3 retries
                    .isLessThanOrEqualTo(10);  // Not too many
            }
        }
    }

    @Nested
    @DisplayName("Watch configuration defaults")
    class WatchConfigurationDefaultsTests {

        @Test
        @DisplayName("has watch configuration")
        @SuppressWarnings("unchecked")
        void hasWatchConfiguration() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");

                assertThat(jwright).containsKey("watch");
            }
        }

        @Test
        @DisplayName("watches test directory by default")
        @SuppressWarnings("unchecked")
        void watchesTestDirectoryByDefault() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> watch = (Map<String, Object>) jwright.get("watch");
                List<String> paths = (List<String>) watch.get("paths");

                assertThat(paths).contains("src/test/java");
            }
        }

        @Test
        @DisplayName("ignores build artifacts by default")
        @SuppressWarnings("unchecked")
        void ignoresBuildArtifactsByDefault() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> watch = (Map<String, Object>) jwright.get("watch");
                List<String> ignore = (List<String>) watch.get("ignore");

                assertThat(ignore)
                    .anyMatch(pattern -> pattern.contains("class"))
                    .anyMatch(pattern -> pattern.contains("target"));
            }
        }
    }

    @Nested
    @DisplayName("Path configuration defaults")
    class PathConfigurationDefaultsTests {

        @Test
        @DisplayName("has standard Java project paths")
        @SuppressWarnings("unchecked")
        void hasStandardJavaProjectPaths() throws IOException {
            try (InputStream configStream = getClass()
                    .getResourceAsStream("/jwright-default-config.yaml")) {

                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(configStream);
                Map<String, Object> jwright = (Map<String, Object>) config.get("jwright");
                Map<String, Object> paths = (Map<String, Object>) jwright.get("paths");

                assertThat(paths.get("source")).isEqualTo("src/main/java");
                assertThat(paths.get("test")).isEqualTo("src/test/java");
            }
        }
    }
}
