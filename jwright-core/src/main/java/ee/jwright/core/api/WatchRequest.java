package ee.jwright.core.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Request to watch for file changes and auto-implement tests.
 * <p>
 * Specifies the project directory, paths to watch, patterns to ignore,
 * debounce duration, and logging level.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param projectDir     the project root directory (must contain a build file)
 * @param watchPaths     paths to watch for changes (relative to projectDir, empty = use defaults)
 * @param ignorePatterns glob patterns to ignore (e.g., "**&#47;*.class", "**&#47;target/**")
 * @param debounce       time to wait after last change before triggering (prevents rapid re-runs)
 * @param logLevel       the logging verbosity level
 */
public record WatchRequest(
    Path projectDir,
    List<Path> watchPaths,
    List<String> ignorePatterns,
    Duration debounce,
    LogLevel logLevel
) {}
