package ee.jwright.core.extract;

import java.nio.file.Path;

/**
 * Request to extract context from a test.
 * <p>
 * Contains all information needed to locate and parse the test,
 * find the implementation, and extract context for code generation.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param testFile         path to the test file
 * @param testClassName    name of the test class
 * @param testMethodName   name of the test method
 * @param implFile         path to the implementation file
 * @param targetMethodName name of the method to implement
 * @param sourceRoot       the source root directory (for resolving imports)
 */
public record ExtractionRequest(
    Path testFile,
    String testClassName,
    String testMethodName,
    Path implFile,
    String targetMethodName,
    Path sourceRoot
) {}
