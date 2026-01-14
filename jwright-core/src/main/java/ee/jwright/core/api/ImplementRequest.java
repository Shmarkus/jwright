package ee.jwright.core.api;

import java.nio.file.Path;

/**
 * Request to implement code for a specific test.
 * <p>
 * Specifies the project directory, target test (class#method), and execution options.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param projectDir the project root directory (must contain a build file)
 * @param target     the test to implement in format "TestClass#testMethod"
 * @param dryRun     if true, show generated code without writing to disk
 * @param logLevel   the logging verbosity level
 */
public record ImplementRequest(
    Path projectDir,
    String target,
    boolean dryRun,
    LogLevel logLevel
) {}
