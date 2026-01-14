package ee.jwright.engine.pipeline;

import ee.jwright.core.build.CompilationError;
import ee.jwright.core.build.TestFailure;

/**
 * Record of a failed attempt during pipeline execution.
 * <p>
 * Captures all relevant information about why an attempt failed,
 * including the generated code, error message, and either compilation
 * or test failure details.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This record is internal and may evolve, but honors the stable contracts.</p>
 *
 * @param attempt          the attempt number (1-based)
 * @param generatedCode    the code that was generated for this attempt
 * @param errorMessage     general error message describing the failure
 * @param compilationError compilation error details (null if not a compilation failure)
 * @param testFailure      test failure details (null if not a test failure)
 */
public record FailedAttempt(
    int attempt,
    String generatedCode,
    String errorMessage,
    CompilationError compilationError,
    TestFailure testFailure
) {}
