package ee.jwright.core.build;

/**
 * Represents a test failure from the test runner.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param testClass  fully qualified name of the test class
 * @param testMethod name of the test method that failed
 * @param message    the failure message
 * @param stackTrace the stack trace (may be null)
 */
public record TestFailure(
    String testClass,
    String testMethod,
    String message,
    String stackTrace
) {}
