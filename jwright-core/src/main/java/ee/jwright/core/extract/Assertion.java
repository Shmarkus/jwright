package ee.jwright.core.extract;

/**
 * Represents an assertion extracted from a test method.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param type     the assertion type (e.g., "assertEquals", "assertThat")
 * @param expected the expected value expression
 * @param actual   the actual value expression
 * @param message  optional assertion message
 */
public record Assertion(
    String type,
    String expected,
    String actual,
    String message
) {}
