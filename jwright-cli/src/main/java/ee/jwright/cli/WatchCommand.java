package ee.jwright.cli;

import ee.jwright.core.api.JwrightCore;
import ee.jwright.core.api.LogLevel;
import ee.jwright.core.api.PipelineResult;
import ee.jwright.core.api.WatchCallback;
import ee.jwright.core.api.WatchHandle;
import ee.jwright.core.api.WatchRequest;
import ee.jwright.core.exception.JwrightException;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command to watch for test file changes and auto-implement.
 * <p>
 * Monitors test files for changes and automatically runs the jwright
 * implementation pipeline when new failing tests are detected.
 * </p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * jwright watch [options]
 * </pre>
 *
 * @since 1.0.0
 */
@Component
@Command(
    name = "watch",
    description = "Watch for test changes and auto-implement",
    mixinStandardHelpOptions = true
)
public class WatchCommand implements Callable<Integer> {

    private final JwrightCore core;

    @Option(
        names = {"--dir", "-d"},
        description = "Project directory (default: current directory)",
        defaultValue = "."
    )
    private Path projectDir;

    @Option(
        names = {"--path", "-p"},
        description = "Paths to watch (relative to project directory)",
        split = ","
    )
    private List<Path> watchPaths;

    @Option(
        names = "--debounce",
        description = "Debounce duration in milliseconds",
        defaultValue = "500"
    )
    private long debounceMs;

    @Option(
        names = {"--verbose", "-v"},
        description = "Show detailed output"
    )
    private boolean verbose;

    @Option(
        names = {"--quiet", "-q"},
        description = "Minimal output"
    )
    private boolean quiet;

    // For testing only
    private Duration timeout;

    /**
     * Creates a new WatchCommand with the JwrightCore dependency.
     *
     * @param core the jwright core API
     */
    public WatchCommand(JwrightCore core) {
        this.core = core;
    }

    @Override
    public Integer call() {
        try {
            Path absolutePath = projectDir.toAbsolutePath();

            if (!quiet) {
                System.out.println("Starting watch mode in: " + absolutePath);
                System.out.println("Press Ctrl+C to stop");
            }

            // Determine log level
            LogLevel logLevel = LogLevel.INFO;
            if (verbose) {
                logLevel = LogLevel.DEBUG;
            } else if (quiet) {
                logLevel = LogLevel.QUIET;
            }

            // Create watch request
            WatchRequest request = new WatchRequest(
                absolutePath,
                watchPaths != null ? watchPaths : List.of(),
                List.of("**/*.class", "**/target/**"),
                Duration.ofMillis(debounceMs),
                logLevel
            );

            // Create callback
            WatchCallback callback = new ConsoleWatchCallback(quiet, verbose);

            // Start watching
            WatchHandle handle = core.watch(request, callback);

            // Wait for shutdown (or timeout in tests)
            if (timeout != null) {
                // For testing - wait for timeout
                long endTime = System.currentTimeMillis() + timeout.toMillis();
                while (handle.isRunning() && System.currentTimeMillis() < endTime) {
                    Thread.sleep(50);
                }
            } else {
                // Normal operation - wait until stopped
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (handle.isRunning()) {
                        handle.stop();
                        if (!quiet) {
                            System.out.println("\nWatch mode stopped");
                        }
                    }
                }));

                while (handle.isRunning()) {
                    Thread.sleep(100);
                }
            }

            return ExitCode.SUCCESS;

        } catch (JwrightException e) {
            System.err.println("Watch error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return mapExceptionToExitCode(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExitCode.SUCCESS;
        }
    }

    private int mapExceptionToExitCode(JwrightException e) {
        return switch (e.getCode()) {
            case CONFIG_INVALID -> ExitCode.CONFIG_ERROR;
            case NO_BUILD_TOOL -> ExitCode.BUILD_TOOL_NOT_FOUND;
            default -> ExitCode.IMPLEMENTATION_FAILED;
        };
    }

    /**
     * Console implementation of WatchCallback.
     */
    private static class ConsoleWatchCallback implements WatchCallback {
        private final boolean quiet;
        private final boolean verbose;

        ConsoleWatchCallback(boolean quiet, boolean verbose) {
            this.quiet = quiet;
            this.verbose = verbose;
        }

        @Override
        public void onFileChanged(Path file) {
            if (verbose) {
                System.out.println("File changed: " + file);
            }
        }

        @Override
        public void onTestDetected(String testTarget) {
            if (!quiet) {
                System.out.println("Test detected: " + testTarget);
            }
        }

        @Override
        public void onGenerationStarted(String testTarget) {
            if (!quiet) {
                System.out.println("Generating implementation for: " + testTarget);
            }
        }

        @Override
        public void onGenerationComplete(PipelineResult result) {
            if (quiet) {
                return;
            }

            if (result.success()) {
                System.out.println("Implementation successful!");
                if (verbose && result.finalCode() != null) {
                    System.out.println("Generated code:");
                    System.out.println(result.finalCode());
                }
            } else {
                System.out.println("Implementation failed");
                if (verbose && result.taskResults() != null) {
                    result.taskResults().stream()
                        .filter(r -> r.status() == ee.jwright.core.task.TaskStatus.FAILED)
                        .forEach(r -> System.out.println("  " + r.taskId() + ": " + r.message()));
                }
            }
        }

        @Override
        public void onError(JwrightException error) {
            System.err.println("Error: " + error.getMessage());
            if (verbose) {
                error.printStackTrace();
            }
        }
    }

    // For testing
    void setProjectDir(Path projectDir) {
        this.projectDir = projectDir;
    }

    void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
