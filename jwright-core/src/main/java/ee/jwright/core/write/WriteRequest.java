package ee.jwright.core.write;

import java.nio.file.Path;

/**
 * Request to write generated code to a file.
 * <p>
 * Specifies the target file, method name (if applicable), generated code,
 * and the write mode to use.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param targetFile       path to the target file
 * @param targetMethodName name of the method to write (null for CREATE mode)
 * @param generatedCode    the generated code to write
 * @param mode             the write mode
 */
public record WriteRequest(
    Path targetFile,
    String targetMethodName,
    String generatedCode,
    WriteMode mode
) {}
