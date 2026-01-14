package ee.jwright.core.write;

/**
 * Mode for writing generated code to a file.
 *
 * <h2>Stability: STABLE</h2>
 * <p>This enum is part of the stable API and will not change in backwards-incompatible ways.</p>
 */
public enum WriteMode {

    /**
     * Insert method body into an existing empty method.
     * <p>
     * Used when the method signature exists but the body is empty or contains
     * only a placeholder (like {@code throw new UnsupportedOperationException()}).
     * </p>
     */
    INJECT,

    /**
     * Replace the entire method (signature and body).
     * <p>
     * Used when regenerating a method completely, including any signature changes.
     * </p>
     */
    REPLACE,

    /**
     * Add a new method to an existing class.
     * <p>
     * Used when the method doesn't exist yet and needs to be added to the class.
     * </p>
     */
    APPEND,

    /**
     * Create a new file with the generated content.
     * <p>
     * Used when generating an entirely new class file.
     * </p>
     */
    CREATE
}
