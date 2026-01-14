package ee.jwright.core.write;

/**
 * A pluggable code writer that writes generated code to files.
 * <p>
 * Writers are language-specific and handle the mechanics of inserting,
 * replacing, or appending code to source files.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @see WriteRequest
 * @see WriteResult
 * @see WriteMode
 */
public interface CodeWriter {

    /**
     * Returns the unique identifier for this writer.
     * <p>
     * Used for logging and configuration.
     * Examples: "java-method-body", "kotlin-method-body"
     * </p>
     *
     * @return the writer identifier
     */
    String getId();

    /**
     * Returns the execution order for this writer.
     * <p>
     * Lower numbers are preferred. When multiple writers support a request,
     * the one with the lowest order is used.
     * </p>
     *
     * @return the execution order (lower = preferred)
     */
    int getOrder();

    /**
     * Determines whether this writer supports the given request.
     * <p>
     * Called before writing to allow language-specific writers to opt out.
     * For example, a Java writer would return false for Kotlin files.
     * </p>
     *
     * @param request the write request
     * @return true if this writer can handle the request
     */
    boolean supports(WriteRequest request);

    /**
     * Writes the generated code to the target file.
     * <p>
     * Called only if {@link #supports} returned true. Should write the code
     * according to the specified {@link WriteMode} and return the result.
     * </p>
     *
     * @param request the write request
     * @return the result of the write operation
     */
    WriteResult write(WriteRequest request);
}
