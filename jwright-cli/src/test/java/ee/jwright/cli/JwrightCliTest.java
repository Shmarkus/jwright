package ee.jwright.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JwrightCli}.
 */
class JwrightCliTest {

    /**
     * Creates a JwrightCli for testing with a simple reflection-based factory.
     */
    private JwrightCli createCli() {
        return new JwrightCli(CommandLine.defaultFactory());
    }

    @Nested
    @DisplayName("8.1 CLI skeleton")
    class CliSkeletonTests {

        @Test
        @DisplayName("JwrightCli has version option")
        void hasVersionOption() {
            // Given
            CommandLine cmd = new CommandLine(createCli());

            // When
            boolean hasVersion = cmd.isVersionHelpRequested();

            // Then - version option exists even if not requested
            assertThat(cmd.getCommandSpec().optionsMap().containsKey("--version") ||
                       cmd.getCommandSpec().optionsMap().containsKey("-V")).isTrue();
        }

        @Test
        @DisplayName("JwrightCli has help option")
        void hasHelpOption() {
            // Given
            CommandLine cmd = new CommandLine(createCli());

            // Then
            assertThat(cmd.getCommandSpec().optionsMap().containsKey("--help") ||
                       cmd.getCommandSpec().optionsMap().containsKey("-h")).isTrue();
        }

        @Test
        @DisplayName("JwrightCli has init subcommand")
        void hasInitSubcommand() {
            // Given
            CommandLine cmd = new CommandLine(createCli());

            // Then
            assertThat(cmd.getSubcommands().containsKey("init")).isTrue();
        }

        @Test
        @DisplayName("JwrightCli has implement subcommand")
        void hasImplementSubcommand() {
            // Given
            CommandLine cmd = new CommandLine(createCli());

            // Then
            assertThat(cmd.getSubcommands().containsKey("implement")).isTrue();
        }
    }
}
