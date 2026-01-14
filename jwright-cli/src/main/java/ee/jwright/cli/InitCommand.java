package ee.jwright.cli;

import ee.jwright.core.api.JwrightCore;
import ee.jwright.core.exception.JwrightException;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command to initialize jwright configuration for a project.
 * <p>
 * Creates the {@code .jwright/config.yaml} configuration file and
 * the {@code .jwright/templates/} directory for custom templates.
 * </p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * jwright init [--dir &lt;path&gt;]
 * </pre>
 *
 * @since 1.0.0
 */
@Component
@Command(
    name = "init",
    description = "Initialize jwright configuration for a project",
    mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    private final JwrightCore core;

    @Option(
        names = {"--dir", "-d"},
        description = "Project directory (default: current directory)",
        defaultValue = "."
    )
    private Path projectDir;

    @Option(
        names = "--dry-run",
        description = "Show what would be created without making changes"
    )
    private boolean dryRun;

    /**
     * Creates a new InitCommand with the JwrightCore dependency.
     *
     * @param core the jwright core API
     */
    public InitCommand(JwrightCore core) {
        this.core = core;
    }

    @Override
    public Integer call() {
        try {
            Path absolutePath = projectDir.toAbsolutePath();

            if (dryRun) {
                System.out.println("[DRY RUN] Would create:");
                System.out.println("  " + absolutePath.resolve(".jwright/config.yaml"));
                System.out.println("  " + absolutePath.resolve(".jwright/templates/"));
                return ExitCode.SUCCESS;
            }

            core.init(absolutePath);
            System.out.println("Initialized jwright in: " + absolutePath);
            return ExitCode.SUCCESS;

        } catch (JwrightException e) {
            System.err.println("Failed to initialize: " + e.getMessage());
            return ExitCode.CONFIG_ERROR;
        }
    }
}
