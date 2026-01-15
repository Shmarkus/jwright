package ee.jwright.core.build;

import java.nio.file.Path;

/**
 * Represents a compilation error from the build tool.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param file    path to the file with the error
 * @param line    line number of the error (1-based)
 * @param message the error message from the compiler
 */
public record CompilationError(
    Path file,
    int line,
    String message
) {}
