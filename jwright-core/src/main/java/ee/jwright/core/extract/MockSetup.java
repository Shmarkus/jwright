package ee.jwright.core.extract;

/**
 * Represents a mock setup (when/thenReturn) extracted from a test.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param mockObject  the mock object expression
 * @param method      the method being stubbed
 * @param returnValue the value to return
 */
public record MockSetup(
    String mockObject,
    String method,
    String returnValue
) {}
