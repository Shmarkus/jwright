package ee.jwright.cli;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

/**
 * Main entry point for the jwright CLI application.
 * <p>
 * Uses Spring Boot for dependency injection and Picocli for command-line parsing.
 * </p>
 *
 * <h2>Commands:</h2>
 * <ul>
 *   <li>{@code jwright init} - Initialize project configuration</li>
 *   <li>{@code jwright implement} - Generate implementation for test</li>
 *   <li>{@code jwright watch} - Watch mode for continuous TDD</li>
 * </ul>
 *
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "ee.jwright")
@Command(
    name = "jwright",
    mixinStandardHelpOptions = true,
    version = "jwright 1.0.0-SNAPSHOT",
    description = "AI-assisted Test-Driven Development tool",
    subcommands = {
        InitCommand.class,
        ImplementCommand.class,
        WatchCommand.class
    }
)
public class JwrightCli implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private int exitCode;

    /**
     * Creates a new JwrightCli with the Picocli factory.
     *
     * @param factory the Picocli Spring factory for dependency injection
     */
    @Autowired
    public JwrightCli(IFactory factory) {
        this.factory = factory;
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(JwrightCli.class, args)));
    }

    @Override
    public void run(String... args) throws Exception {
        CommandLine cmd = new CommandLine(this, factory);
        exitCode = cmd.execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
