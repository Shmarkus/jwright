package ee.jwright.core.extract;

import java.util.List;

/**
 * Represents a method signature.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param name       the method name
 * @param returnType the return type
 * @param parameters the parameter types and names
 */
public record MethodSignature(
    String name,
    String returnType,
    List<String> parameters
) {}
