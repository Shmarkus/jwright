package ee.jwright.core.extract;

import java.util.List;

/**
 * Represents a type definition (class, interface, record) referenced in a test.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param name    the fully qualified type name
 * @param fields  the field declarations
 * @param methods the method signatures
 */
public record TypeDefinition(
    String name,
    List<String> fields,
    List<MethodSignature> methods
) {}
