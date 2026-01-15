package ee.jwright.core.build;

import java.util.List;

/**
 * Result of compiling project code.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param success true if compilation succeeded
 * @param errors  list of compilation errors (empty on success)
 */
public record CompilationResult(
    boolean success,
    List<CompilationError> errors
) {}
