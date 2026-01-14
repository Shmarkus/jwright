package ee.jwright.core.extract;

/**
 * Represents a mock verification statement extracted from a test.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param mockObject the mock object expression
 * @param method     the method being verified
 * @param times      the expected invocation count expression (e.g., "1", "never()")
 */
public record VerifyStatement(
    String mockObject,
    String method,
    String times
) {}
