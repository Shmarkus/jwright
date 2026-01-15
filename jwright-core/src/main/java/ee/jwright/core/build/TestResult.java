package ee.jwright.core.build;

import java.util.List;

/**
 * Result of running tests.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param success  true if all tests passed
 * @param passed   number of tests that passed
 * @param failed   number of tests that failed
 * @param failures list of test failures (empty on success)
 */
public record TestResult(
    boolean success,
    int passed,
    int failed,
    List<TestFailure> failures
) {}
