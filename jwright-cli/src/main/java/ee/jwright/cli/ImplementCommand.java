package ee.jwright.cli;

import ee.jwright.core.api.ImplementRequest;
import ee.jwright.core.api.JwrightCore;
import ee.jwright.core.api.LogLevel;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.exception.JwrightException;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command to generate implementation for a failing test.
 * <p>
 * Runs the jwright pipeline to analyze the test, generate code,
 * and validate that the implementation passes.
 * </p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * jwright implement TestClass#testMethod [options]
 * </pre>
 *
 * @since 1.0.0
 */
@Component
@Command(
    name = "implement",
    description = "Generate implementation for a failing test",
    mixinStandardHelpOptions = true
)
public class ImplementCommand implements Callable<Integer> {

    private final JwrightCore core;

    @Parameters(
        index = "0",
        description = "Test target in format TestClass#testMethod"
    )
    private String target;

    @Option(
        names = {"--dir", "-d"},
        description = "Project directory (default: current directory)",
        defaultValue = "."
    )
    private Path projectDir;

    @Option(
        names = "--no-refactor",
        description = "Skip the refactoring step"
    )
    private boolean noRefactor;

    @Option(
        names = "--dry-run",
        description = "Show generated code without writing to files"
    )
    private boolean dryRun;

    @Option(
        names = {"--verbose", "-v"},
        description = "Show detailed output including prompts and responses"
    )
    private boolean verbose;

    @Option(
        names = {"--quiet", "-q"},
        description = "Minimal output - only errors and final result"
    )
    private boolean quiet;

    /**
     * Creates a new ImplementCommand with the JwrightCore dependency.
     *
     * @param core the jwright core API
     */
    public ImplementCommand(JwrightCore core) {
        this.core = core;
    }

    @Override
    public Integer call() {
        try {
            // Parse target
            String[] parts = target.split("#");
            if (parts.length != 2) {
                System.err.println("Invalid target format. Expected: TestClass#testMethod");
                return ExitCode.INVALID_ARGS;
            }

            String testClassName = parts[0];
            String testMethodName = parts[1];
            Path absolutePath = projectDir.toAbsolutePath();

            if (!quiet) {
                System.out.println("Implementing " + testClassName + "#" + testMethodName + "...");
            }

            // Determine log level
            LogLevel logLevel = LogLevel.INFO;
            if (verbose) {
                logLevel = LogLevel.DEBUG;
            } else if (quiet) {
                logLevel = LogLevel.QUIET;
            }

            // Create request
            ImplementRequest request = new ImplementRequest(
                absolutePath,
                target,
                dryRun,
                logLevel
            );

            // Run pipeline
            PipelineResult result = core.implement(request);

            if (result.success()) {
                if (!quiet) {
                    System.out.println("Implementation successful!");
                    if (result.finalCode() != null) {
                        System.out.println("\nGenerated code:");
                        System.out.println(result.finalCode());
                    }
                    if (result.modifiedFile() != null) {
                        System.out.println("\nWritten to: " + result.modifiedFile());
                    }
                }
                return ExitCode.SUCCESS;
            } else {
                // Get failure message from task results
                String message = result.taskResults().stream()
                    .filter(r -> r.status() == ee.jwright.core.task.TaskStatus.FAILED)
                    .findFirst()
                    .map(r -> r.message())
                    .orElse("Unknown error");
                System.err.println("Implementation failed: " + message);
                return ExitCode.IMPLEMENTATION_FAILED;
            }

        } catch (JwrightException e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return mapExceptionToExitCode(e);
        }
    }

    private int mapExceptionToExitCode(JwrightException e) {
        return switch (e.getCode()) {
            case CONFIG_INVALID -> ExitCode.CONFIG_ERROR;
            case NO_BUILD_TOOL -> ExitCode.BUILD_TOOL_NOT_FOUND;
            default -> ExitCode.IMPLEMENTATION_FAILED;
        };
    }
}
